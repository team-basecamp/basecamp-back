package com.basecamp.backend.domain.post.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// keepImageUrls 의 형식 검증만 확인한다. "이 게시글의 이미지인가"는 PostService 책임이라 여기서 다루지 않는다.
class PostUpdateRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private Set<String> validateKeepImageUrls(List<String> keepImageUrls) {
    PostUpdateRequest request = new PostUpdateRequest("GENERAL", "제목", "내용", keepImageUrls);
    return validator.validate(request).stream()
        .filter(v -> v.getPropertyPath().toString().startsWith("keepImageUrls"))
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("검증_필드가_null이면_통과한다")
  void 검증_필드가_null이면_통과한다() {
    // given: 이미지를 건드리지 않는 수정 — 필드 자체를 생략한 경우

    // when
    Set<String> messages = validateKeepImageUrls(null);

    // then: null 은 "기존 전부 유지" 의미라 제약을 걸지 않는다
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("검증_빈_목록이면_통과한다")
  void 검증_빈_목록이면_통과한다() {
    // given: 기존 이미지를 전부 삭제하겠다는 의미의 빈 배열

    // when
    Set<String> messages = validateKeepImageUrls(List.of());

    // then
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("검증_정상_상대경로면_통과한다")
  void 검증_정상_상대경로면_통과한다() {
    // given: 저장소가 실제로 돌려주는 형태의 상대경로들
    List<String> urls = List.of("/images/abc123.jpg", "/images/nested/photo.webp");

    // when
    Set<String> messages = validateKeepImageUrls(urls);

    // then
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("검증_접두어가_images가_아니어도_통과한다")
  void 검증_접두어가_images가_아니어도_통과한다() {
    // given: file.upload.url-prefix 는 설정으로 바뀔 수 있으므로 접두어를 못 박으면 안 된다
    List<String> urls = List.of("/uploads/abc123.jpg");

    // when
    Set<String> messages = validateKeepImageUrls(urls);

    // then
    assertThat(messages).isEmpty();
  }

  @Test
  @DisplayName("검증_원소가_공백이면_거부한다")
  void 검증_원소가_공백이면_거부한다() {
    // given
    List<String> urls = List.of("   ");

    // when
    Set<String> messages = validateKeepImageUrls(urls);

    // then
    assertThat(messages).contains("유지할 이미지 경로는 비어 있을 수 없습니다.");
  }

  @Test
  @DisplayName("검증_원소가_null이면_거부한다")
  void 검증_원소가_null이면_거부한다() {
    // given: List.of 는 null 을 못 담으므로 null 을 허용하는 목록으로 만든다
    List<String> urls = Collections.singletonList(null);

    // when
    Set<String> messages = validateKeepImageUrls(urls);

    // then
    assertThat(messages).contains("유지할 이미지 경로는 비어 있을 수 없습니다.");
  }

  @Test
  @DisplayName("검증_상대경로_꼴이_아니면_거부한다")
  void 검증_상대경로_꼴이_아니면_거부한다() {
    // given: 슬래시로 시작하지 않는 경로, 절대 URL, 상위 경로 탈출, 공백 포함
    List<String> invalid =
        List.of(
            "images/abc.jpg",
            "http://evil.example.com/x.jpg",
            "/images/../../etc/passwd",
            "/images/a b.jpg");

    // when / then
    for (String url : invalid) {
      assertThat(validateKeepImageUrls(List.of(url)))
          .as("거부해야 하는 경로: %s", url)
          .contains("유지할 이미지 경로는 \"/\" 로 시작하는 상대경로여야 합니다.");
    }
  }

  @Test
  @DisplayName("검증_목록이_상한을_넘으면_거부한다")
  void 검증_목록이_상한을_넘으면_거부한다() {
    // given: 페이로드 방어선(50)을 넘긴 목록
    List<String> urls =
        IntStream.rangeClosed(1, 51).mapToObj(i -> "/images/img" + i + ".jpg").toList();

    // when
    Set<String> messages = validateKeepImageUrls(urls);

    // then
    assertThat(messages).contains("유지할 이미지는 최대 50개까지 지정할 수 있습니다.");
  }
}
