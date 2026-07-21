package com.basecamp.backend.common.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 이미지 저장소(MinIO) 설정. {@code minio.*} 프로퍼티로 주입된다.
 *
 * <p>버킷 하나를 쓰고 그 안에서 {@link ImageCategory} 접두어로 용도를 나눈다. 버킷은 anonymous download 정책이므로 저장된 객체의 공개
 * URL을 그대로 DB에 남긴다. 덕분에 응답 DTO는 URL 조립 없이 값을 그대로 내보낼 수 있다.
 *
 * <p>{@link #endpoint}와 {@link #publicEndpoint}를 나눈 이유는, 서버가 접속하는 주소와 클라이언트가 접근하는 주소가 갈릴 수 있기 때문이다.
 * 컨테이너 안에서는 {@code http://minio:9000}, 브라우저에서는 {@code http://localhost:9000} 이 되는 식이다. 로컬 기본값은 둘이
 * 같다.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

  /** 서버가 MinIO SDK로 접속할 주소. */
  @NotBlank private String endpoint;

  /** 클라이언트에 노출할 주소. DB에 저장되는 이미지 URL의 접두어가 되므로, 바뀌면 기존에 저장된 URL과 어긋난다는 점에 유의한다. */
  @NotBlank private String publicEndpoint;

  @NotBlank private String accessKey;

  @NotBlank private String secretKey;

  /** 이미지를 담을 버킷. 생성과 공개 정책 설정은 docker-compose 의 minio-init 이 담당한다. */
  @NotBlank private String bucket;

  /** 한 요청에 첨부할 수 있는 이미지 최대 개수. */
  @Positive private int maxCount = 5;

  /** 허용 확장자(소문자, 점 제외). 이 목록 밖이면 저장을 거부한다. */
  private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");

  public void setEndpoint(String endpoint) {
    this.endpoint = stripTrailingSlash(endpoint);
  }

  public void setPublicEndpoint(String publicEndpoint) {
    this.publicEndpoint = stripTrailingSlash(publicEndpoint);
  }

  /**
   * 이 저장소가 발급한 URL 인지 판별하는 기준이자, 객체 키를 붙일 경계(예: {@code http://localhost:9000/basecamp/}).
   *
   * <p>삭제 시 이 접두어로 시작하지 않는 URL(소셜 로그인이 준 외부 프로필 이미지 등)은 우리 관리 대상이 아니므로 건드리지 않는다.
   */
  public String publicBaseUrl() {
    return publicEndpoint + "/" + bucket + "/";
  }

  /** 객체 키에 대응하는 공개 URL. 이 값이 그대로 DB와 응답에 실린다. */
  public String publicUrl(String objectKey) {
    return publicBaseUrl() + objectKey;
  }

  /** 접두어를 붙일 때 슬래시가 겹치지 않도록 끝 슬래시를 떼어 보관한다. */
  private static String stripTrailingSlash(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    while (trimmed.length() > 1 && trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
