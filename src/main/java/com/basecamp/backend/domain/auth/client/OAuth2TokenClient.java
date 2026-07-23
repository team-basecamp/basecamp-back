package com.basecamp.backend.domain.auth.client;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OAuth2 인가 코드 → access token 교환(코드 릴레이 공통 로직).
 *
 * <p>{@code grant_type=authorization_code} 표준 토큰 요청은 provider 무관하게 동일하므로, 엔드포인트/자격증명이 담긴 {@link
 * ClientRegistration} 만 주입받아 카카오/구글/네이버가 공통으로 재사용한다. ({@code client_id}/{@code client_secret} 은
 * body 로 전송 — {@code client_secret_post})
 */
@Component
@RequiredArgsConstructor
public class OAuth2TokenClient {

  private final RestClient restClient;

  public String fetchAccessToken(ClientRegistration registration, String authorizationCode) {
    return fetchAccessToken(registration, authorizationCode, null);
  }

  public String fetchAccessToken(
      ClientRegistration registration, String authorizationCode, String state) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", registration.getClientId());
    form.add("client_secret", registration.getClientSecret());
    form.add("redirect_uri", registration.getRedirectUri());
    form.add("code", authorizationCode);
    // 네이버는 토큰 교환에 state 를 요구한다. 카카오/구글은 사용하지 않으므로 값이 있을 때만 싣는다.
    if (StringUtils.hasText(state)) {
      form.add("state", state);
    }

    OAuth2TokenResponse response;
    try {
      response =
          restClient
              .post()
              .uri(registration.getProviderDetails().getTokenUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(OAuth2TokenResponse.class);
    } catch (RestClientException e) {
      // 잘못된/만료된 code, provider 4xx·5xx, 통신 오류 등을 공통 실패로 정규화한다.
      throw new BusinessException(ErrorCode.SOCIAL_TOKEN_FETCH_FAILED);
    }

    if (response == null || !StringUtils.hasText(response.accessToken())) {
      throw new BusinessException(ErrorCode.SOCIAL_TOKEN_FETCH_FAILED);
    }
    return response.accessToken();
  }
}
