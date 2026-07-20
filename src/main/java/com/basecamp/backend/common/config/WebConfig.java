package com.basecamp.backend.common.config;

import com.basecamp.backend.common.storage.FileStorageProperties;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 리소스(이미지) 제공 설정.
 *
 * <p>{@code /images/**} 한 경로로 두 곳을 함께 제공한다.
 *
 * <ol>
 *   <li>런타임 업로드 이미지 — {@link FileStorageProperties#getDir()}(예: {@code uploads/images}). Git 제외.
 *   <li>고정 더미 이미지 — {@code classpath:/static/images/}. Git 관리.
 * </ol>
 *
 * 업로드 위치를 먼저 조회하고 없으면 더미(classpath)로 넘어간다. 덕분에 DB에 저장된 {@code /images/<파일명>} 상대경로 하나로 업로드본과 더미를 구분
 * 없이 접근할 수 있다.
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final FileStorageProperties properties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 업로드 디렉터리를 절대경로 file: URI 로 변환한다. 디렉터리이므로 끝에 슬래시를 보장한다.
    String uploadLocation =
        Paths.get(properties.getDir()).toAbsolutePath().normalize().toUri().toString();
    if (!uploadLocation.endsWith("/")) {
      uploadLocation = uploadLocation + "/";
    }

    registry
        .addResourceHandler(properties.getUrlPrefixPattern())
        .addResourceLocations(uploadLocation, "classpath:/static/images/");
  }
}
