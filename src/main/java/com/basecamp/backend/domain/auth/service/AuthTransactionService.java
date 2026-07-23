package com.basecamp.backend.domain.auth.service;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.dto.response.LoginResponse;
import com.basecamp.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.basecamp.backend.domain.auth.entity.BlacklistReason;
import com.basecamp.backend.domain.auth.entity.TokenBlacklist;
import com.basecamp.backend.domain.auth.repository.TokenBlacklistRepository;
import com.basecamp.backend.domain.user.entity.Image;
import com.basecamp.backend.domain.user.entity.User;
import com.basecamp.backend.domain.user.repository.ImageRepository;
import com.basecamp.backend.domain.user.repository.UserRepository;
import com.basecamp.backend.security.cache.TokenBlacklistCache;
import com.basecamp.backend.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인의 DB 트랜잭션 구간.
 *
 * <p>외부 소셜 호출을 제외한 {@code email 기준 upsert + 자체 JWT 발급 + 응답 조립}만 짧은 트랜잭션으로 수행한다. 외부 HTTP 지연이 DB 커넥션
 * 점유로 이어지지 않도록, 외부 호출은 {@link AuthService} 가 트랜잭션 밖에서 먼저 수행하고 그 결과({@link OAuthUserInfo})만 이 트랜잭션으로
 * 넘긴다.
 *
 * <p>{@code @Transactional} 자기 호출(self-invocation)은 프록시를 우회해 트랜잭션이 열리지 않으므로, 트랜잭션 경계를 실제로 분리하려면 이렇게
 * 별도 빈으로 두어야 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthTransactionService {

  private final UserRepository userRepository;
  private final ImageRepository imageRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final TokenBlacklistCache tokenBlacklistCache;
  private final JwtTokenProvider jwtTokenProvider;
  private final Clock clock;

  /**
   * email 기준으로 회원을 upsert 하고 자체 JWT(access/refresh)를 발급한다.
   *
   * <p>동시 최초 가입 경합으로 {@code email} UNIQUE 제약을 위반하면 {@link
   * org.springframework.dao.DataIntegrityViolationException} 이 전파되며, 재시도 여부는 호출자({@link
   * AuthService})가 결정한다.
   */
  public LoginResult upsertUserAndIssueToken(OAuthUserInfo userInfo) {
    // 탈퇴 회원은 조회되지 않으므로 register 경로를 타 새 user_id 로 재가입한다(옛 행은 그대로 보존).
    User user =
        userRepository
            .findByEmailAndDeletedAtIsNull(userInfo.email())
            .map(existing -> updateExisting(existing, userInfo))
            .orElseGet(() -> register(userInfo));

    String role = user.getRole().name();
    String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);

    // LAZY 연관(profileImage) 접근이 필요하므로 응답 body 는 트랜잭션 내부에서 조립한다.
    return new LoginResult(LoginResponse.from(user, accessToken), refreshToken);
  }

  /**
   * refresh 토큰을 회전(rotation)한다. 이전 토큰을 블랙리스트에 올려 폐기하고 access/refresh 를 새로 발급한다.
   *
   * <p>무상태 JWT 는 서명만 맞으면 만료 전까지 유효하므로, 새 토큰을 내려주는 것만으로는 이전 토큰이 죽지 않는다. 이전 {@code jti} 를 블랙리스트에 등록해야
   * 비로소 "탈취 토큰의 유효 기간 = 다음 정상 재발급까지"가 성립한다(#39).
   *
   * <p>이미 폐기된 jti 로 재발급을 요청하면 탈취 의심으로 보고 거부한다(refresh token reuse detection). 동시 요청으로 {@code
   * existsByJti} 검사를 둘 다 통과하더라도 {@code idx_bl_jti}(UNIQUE) 가 최종 방어선이며, 이때 발생하는 {@link
   * org.springframework.dao.DataIntegrityViolationException} 은 호출자가 401 로 변환한다.
   *
   * <p>role 은 토큰 클레임을 믿지 않고 DB 에서 다시 읽는다. 권한 변경·탈퇴·제재가 재발급 시점에 반영되어야 한다.
   *
   * @param refreshExpiresAt 폐기할 토큰의 만료 시각. 이 시각이 지나면 블랙리스트 레코드를 정리해도 된다.
   */
  public TokenRefreshResult rotateRefreshToken(Long userId, String jti, Instant refreshExpiresAt) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    if (user.isWithdrawn()) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (user.isBlacklisted()) {
      throw new BusinessException(ErrorCode.BLACKLISTED_USER);
    }
    if (tokenBlacklistRepository.existsByJti(jti)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 재사용 탐지는 MySQL(위 existsByJti)이 담당한다. 캐시 미스로 폐기된 refresh 토큰이 되살아나면 안 되기 때문이다.
    // 따라서 이 경로는 Redis 를 쓰지도, 읽지도 않는다(재발급이 Redis 장애에 묶이지 않는다).
    blacklistToken(
        userId, TokenInfo.refresh(jti, refreshExpiresAt), BlacklistReason.REFRESH_ROTATED);

    String role = user.getRole().name();
    String accessToken = jwtTokenProvider.createAccessToken(userId, role);
    String refreshToken = jwtTokenProvider.createRefreshToken(userId, role);
    return new TokenRefreshResult(TokenRefreshResponse.of(accessToken), refreshToken);
  }

  /** 토큰들을 블랙리스트에 올려 폐기한다(로그아웃/탈퇴 시 access + refresh 를 함께 넘긴다). */
  public void blacklistTokens(Long userId, List<TokenInfo> tokens, BlacklistReason reason) {
    tokens.forEach(token -> blacklistToken(userId, token, reason));
  }

  /**
   * 토큰 하나를 블랙리스트에 올려 폐기한다. 이미 등록돼 있으면 아무것도 하지 않는다(로그아웃을 두 번 눌러도 결과는 같아야 한다).
   *
   * <p><b>이중 기록:</b> MySQL 은 영속 기록·감사용, Redis 는 인증 필터가 매 요청 조회하는 캐시다(#39 §5).
   *
   * <p><b>Redis 에는 access 토큰만 올린다.</b> 인증 필터는 access 토큰의 jti 만 조회한다(refresh 토큰으로는 애초에 인증되지 않는다).
   * refresh 의 재사용 탐지는 MySQL {@code existsByJti} 가 담당하므로, refresh jti 를 캐시에 넣으면 한 번도 읽히지 않는 키가 회전마다
   * 최대 refresh 수명(14일)만큼 쌓이고 재발급이 Redis 장애에 묶인다.
   *
   * <p>access 토큰은 Redis 를 <b>먼저</b> 쓴다. DB 가 뒤이어 롤백되면 캐시에만 남는 유령 항목이 생기지만, 그건 "죽지 않아야 할 토큰이 죽는"
   * 방향이라 안전하고 TTL 로 자동 정리된다. 반대 순서였다면 커밋과 캐시 반영 사이에 폐기된 토큰이 통과하는 창이 열린다.
   *
   * <p>동시 요청으로 {@code existsByJti} 검사를 둘 다 통과하면 {@code idx_bl_jti}(UNIQUE) 가 막고 {@link
   * org.springframework.dao.DataIntegrityViolationException} 이 전파된다. 이때도 "폐기됨"이라는 목표 상태는 이미 달성됐으므로
   * 호출자가 무시하거나 재시도한다. (제약 위반 후 같은 트랜잭션을 계속 쓸 수 없어 여기서 삼키지 않는다.)
   */
  public void blacklistToken(Long userId, TokenInfo token, BlacklistReason reason) {
    if (tokenBlacklistRepository.existsByJti(token.jti())) {
      return;
    }
    if (token.accessToken()) {
      tokenBlacklistCache.blacklist(token.jti(), token.expiresAt(), Instant.now(clock));
    }

    LocalDateTime expiresAt = LocalDateTime.ofInstant(token.expiresAt(), clock.getZone());
    tokenBlacklistRepository.saveAndFlush(
        TokenBlacklist.of(userId, token.jti(), reason, expiresAt));
  }

  /**
   * 회원을 탈퇴 처리(soft delete)하고, 함께 넘어온 토큰들을 폐기한다.
   *
   * <p>탈퇴와 토큰 폐기는 한 트랜잭션이어야 한다. 탈퇴만 되고 토큰이 살아 있으면 재발급이 계속 가능해진다.
   *
   * @param tokens 폐기할 access/refresh 토큰. 유효한 토큰이 없으면 빈 리스트
   */
  public void withdrawUser(Long userId, String reason, List<TokenInfo> tokens) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    // 폐기 전에 이미 탈퇴한 회원이라면 중복 요청이다(access 토큰이 아직 살아 있을 수 있다).
    if (user.isWithdrawn()) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    user.withdraw(reason, clock);
    blacklistTokens(userId, tokens, BlacklistReason.WITHDRAWAL);
  }

  private User register(OAuthUserInfo userInfo) {
    Image profileImage = createImageOrNull(userInfo.profileImageUrl());
    User user =
        User.register(userInfo.nickname(), userInfo.email(), profileImage, userInfo.provider());
    return userRepository.save(user);
  }

  private User updateExisting(User user, OAuthUserInfo userInfo) {
    // 제재된 회원은 소셜 로그인으로 새 토큰을 받을 수 없다. 이걸 막지 않으면 재발급을 차단해도 제재가 무력화된다(#18).
    if (user.isBlacklisted()) {
      throw new BusinessException(ErrorCode.BLACKLISTED_USER);
    }

    // email 이 유일 식별키다. 같은 이메일이라도 최초 가입과 다른 provider 로 로그인하면
    // 기존 계정을 덮어쓰지 않고 차단한다(다른 소셜로 가입 시도 → 409).
    // 어떤 소셜로 가입된 계정인지 안내해, 사용자가 올바른 로그인 수단을 고르게 한다.
    if (user.getProvider() != userInfo.provider()) {
      throw new BusinessException(
          ErrorCode.EMAIL_ALREADY_REGISTERED,
          "'%s'로 가입된 회원이므로, 해당 계정으로 로그인해주세요.".formatted(user.getProvider().getDisplayName()));
    }

    // 재로그인 시 소셜 프로필(닉네임 + 이미지)을 동기화한다.
    Image current = user.getProfileImage();

    // 사용자가 직접 올린 프로필(MINIO)은 소셜 로그인이 건드리지 않는다. 소셜 URL 로 덮으면
    // 사용자가 고른 이미지가 사라지고, 저장소에 올린 실물이 참조를 잃어 고아로 남는다. 닉네임만 동기화한다.
    if (current != null && current.isStoredByUs()) {
      user.updateProfile(userInfo.nickname(), current);
      return user;
    }

    Image profileImage = syncImage(current, userInfo.profileImageUrl());
    user.updateProfile(userInfo.nickname(), profileImage);

    // 프로필 이미지가 제거된 경우(새 url 없음 + 기존 이미지 존재): users.image_id 해제만으론 images row 가 고아로 남는다.
    // User 에 orphanRemoval 설정이 없으므로 여기서 기존 row 를 명시적으로 삭제한다.
    // updateProfile 로 FK(image_id)를 먼저 NULL 로 끊은 뒤 삭제하므로 FK 제약 위반은 없다.
    if (profileImage == null && current != null) {
      imageRepository.delete(current);
    }
    return user;
  }

  // 소셜 로그인이 준 프로필 이미지는 제공자 서버의 URL 이다. 우리가 올린 파일이 아니므로 EXTERNAL 로 남긴다.
  private Image createImageOrNull(String imageUrl) {
    return StringUtils.hasText(imageUrl) ? imageRepository.save(Image.ofExternal(imageUrl)) : null;
  }

  private Image syncImage(Image current, String imageUrl) {
    if (!StringUtils.hasText(imageUrl)) {
      return null;
    }
    if (current != null) {
      current.updateExternal(imageUrl);
      return current;
    }
    return imageRepository.save(Image.ofExternal(imageUrl));
  }
}
