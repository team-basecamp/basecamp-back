package com.basecamp.backend.domain.auth.client;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.user.entity.Provider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@link Provider} 에 해당하는 {@link SocialClient} 구현체를 선택한다.
 *
 * <p>등록된 모든 {@code SocialClient} 빈을 provider별로 색인한다. 아직 구현체가 없으면(카카오/구글/네이버 미구현) 빈 맵이 되며, 해당
 * provider 요청 시 {@link ErrorCode#UNSUPPORTED_PROVIDER} 로 거부한다.
 */
@Component
public class SocialClientResolver {

  private final Map<Provider, SocialClient> clientsByProvider;

  public SocialClientResolver(List<SocialClient> clients) {
    this.clientsByProvider =
        clients.stream()
            .collect(Collectors.toUnmodifiableMap(SocialClient::provider, Function.identity()));
  }

  public SocialClient resolve(Provider provider) {
    SocialClient client = clientsByProvider.get(provider);
    if (client == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
    }
    return client;
  }
}
