package com.basecamp.backend.domain.auth.client.naver;

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
 * 네이버 소셜 로그인 어댑터.
 *
 * <p>{@code code(+state) → 토큰 교환(공통 {@link OAuth2TokenClient}) → /v1/nid/me 조회 → {@link
 * OAuthUserInfo} 정규화} 순서로 처리한다. 카카오와 달리 네이버는 토큰 교환에 {@code state}(CSRF 방지 값)를 요구하므로 프론트가 보낸 state 를
 * 그대로 전달한다. 엔드포인트/자격증명은 {@code application.yml} 의 {@link ClientRegistration}(registrationId =
 * {@code naver})을 재사용한다.
 */
@Component
@RequiredArgsConstructor
public class NaverSocialClient implements SocialClient {

  private static final String REGISTRATION_ID = "naver";

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2TokenClient oauth2TokenClient;
  private final RestClient restClient;

  @Override
  public Provider provider() {
    return Provider.NAVER;
  }

  @Override
  public OAuthUserInfo fetchUserInfo(String authorizationCode, String state) {
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
    if (registration == null) {
      throw new BusinessException(ErrorCode.SOCIAL_TOKEN_FETCH_FAILED);
    }
    String accessToken = oauth2TokenClient.fetchAccessToken(registration, authorizationCode, state);
    return requestUser(registration, accessToken).toOAuthUserInfo();
  }

  private NaverUserResponse requestUser(ClientRegistration registration, String accessToken) {
    NaverUserResponse response;
    try {
      response =
          restClient
              .get()
              .uri(registration.getProviderDetails().getUserInfoEndpoint().getUri())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(NaverUserResponse.class);
    } catch (RestClientException e) {
      throw new BusinessException(ErrorCode.SOCIAL_USERINFO_FETCH_FAILED);
    }
    if (response == null || response.response() == null) {
      throw new BusinessException(ErrorCode.SOCIAL_USERINFO_FETCH_FAILED);
    }
    return response;
  }
}
