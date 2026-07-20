package com.basecamp.backend.domain.post.dto.request;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

// 게시글 목록 커서. 목록 정렬 키인 (createdAt DESC, postId DESC)와 정확히 같은 값을 담는다.
// createdAt만으로는 같은 시각의 글을 구분할 수 없어 커서가 그 지점에서 멈추거나 건너뛴다. postId가 그 동점을 깬다.
//
// 클라이언트에는 base64url 문자열로만 노출한다(불투명 커서).
// createdAt/postId를 날것으로 내려주면 프런트가 커서를 직접 조립하기 시작하고,
// 그 순간 정렬 키는 바꿀 수 없는 공개 API 스펙이 된다. 인코딩해 두면 서버가 정렬 키를 자유롭게 바꿀 수 있다.
public record PostCursorRequest(LocalDateTime createdAt, Long postId) {

  // 커서 평문 형식: "{ISO-8601 createdAt}|{postId}"
  private static final char DELIMITER = '|';

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  // 다음 요청에 그대로 실어 보낼 커서 문자열.
  // URL 쿼리 파라미터로 오가므로 표준 base64가 아닌 URL-safe 알파벳(-, _)을 쓰고,
  // 패딩(=)은 쿼리스트링에서 인코딩 이슈를 만들기 쉬워 뺀다.
  public String encode() {
    String plain = FORMATTER.format(createdAt) + DELIMITER + postId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  // 요청으로 들어온 커서 문자열을 되돌린다. 첫 페이지 요청(커서 없음)은 null로 취급한다.
  //
  // 커서는 사용자가 그대로 조작할 수 있는 입력이다. 깨진 값을 그냥 넘기면 500이 나거나,
  // 더 나쁘게는 조건이 빠진 채 첫 페이지가 조용히 반환돼 무한 스크롤이 처음으로 되감긴다. 400으로 끊는다.
  public static PostCursorRequest decode(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      return null;
    }

    try {
      String plain =
          new String(Base64.getUrlDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);

      // split(정규식)이 아닌 indexOf로 자른다. '|'는 정규식 메타문자라 이스케이프가 필요하고,
      // ISO 시각 문자열에는 '|'가 없으므로 첫 구분자 하나만 찾으면 충분하다.
      int delimiter = plain.indexOf(DELIMITER);
      if (delimiter < 0) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
      }

      LocalDateTime createdAt = LocalDateTime.parse(plain.substring(0, delimiter), FORMATTER);
      Long postId = Long.parseLong(plain.substring(delimiter + 1));

      return new PostCursorRequest(createdAt, postId);

    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      // base64 디코딩(IllegalArgumentException), 시각 파싱(DateTimeParseException),
      // id 파싱(NumberFormatException) 모두 "커서가 잘못됐다"는 같은 결론이라 한데 묶는다.
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
