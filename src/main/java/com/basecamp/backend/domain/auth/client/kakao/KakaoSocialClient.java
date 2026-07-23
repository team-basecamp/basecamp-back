package com.basecamp.backend.domain.auth.client.kakao;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.auth.client.OAuth2TokenClient;
import com.basecamp.backend.domain.auth.client.OAuthUserInfo;
import com.basecamp.backend.domain.auth.client.SocialClient;
import com.basecamp.backend.domain.user.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 소셜 로그인 어댑터.
 *
 * <p>{@code code → 토큰 교환(공통 {@link OAuth2TokenClient}) → /v2/user/me 조회 → {@link OAuthUserInfo}
 * 정규화} 순서로 처리한다. 엔드포인트/자격증명은 {@code application.yml} 의 {@code spring.security.oauth2.client} 설정에서
 * 만들어지는 {@link ClientRegistration}(registrationId = {@code kakao})을 재사용한다(수동 코드 릴레이라 {@code
 * oauth2Login()} 은 쓰지 않음).
 */
@Component
@RequiredArgsConstructor
public class KakaoSocialClient implements SocialClient {

  private static final String REGISTRATION_ID = "kakao";

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2TokenClient oauth2TokenClient;
  private final RestClient restClient;

  @Override
  public Provider provider() {
    return Provider.KAKAO;
  }

  @Override
  public OAuthUserInfo fetchUserInfo(String authorizationCode, String state) {
    // 카카오는 state 를 토큰 교환에 사용하지 않는다(파라미터는 인터페이스 공통 시그니처를 위해 받되 무시).
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
    if (registration == null) {
      throw new BusinessException(ErrorCode.SOCIAL_TOKEN_FETCH_FAILED);
    }
    String accessToken = oauth2TokenClient.fetchAccessToken(registration, authorizationCode);
    return requestUser(registration, accessToken).toOAuthUserInfo();
  }

  private KakaoUserResponse requestUser(ClientRegistration registration, String accessToken) {
    KakaoUserResponse response;
    try {
      response =
          restClient
              .get()
              .uri(registration.getProviderDetails().getUserInfoEndpoint().getUri())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(KakaoUserResponse.class);
    } catch (RestClientException e) {
      throw new BusinessException(ErrorCode.SOCIAL_USERINFO_FETCH_FAILED);
    }
    if (response == null) {
      throw new BusinessException(ErrorCode.SOCIAL_USERINFO_FETCH_FAILED);
    }
    return response;
  }
}
