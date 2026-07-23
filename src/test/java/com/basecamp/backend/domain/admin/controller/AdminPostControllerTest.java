package com.basecamp.backend.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.ReportedPostResponse;
import com.basecamp.backend.domain.admin.service.AdminPostService;
import com.basecamp.backend.domain.post.entity.ReportStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AdminPostController} 슬라이스 테스트.
 *
 * <p>{@code ROLE_ADMIN} 인가는 {@code SecurityConfig} 의 관심사이므로 {@code addFilters = false} 로 끄고, 요청/응답
 * 조립·검증과 예외 매핑만 확인한다({@code AdminUserControllerTest} 와 같은 방식).
 */
@WebMvcTest(AdminPostController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPostControllerTest {

  private static final Long POST_ID = 42L;

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminPostService adminPostService;

  private ReportedPostResponse sampleReport() {
    return new ReportedPostResponse(
        10L,
        POST_ID,
        "예약 양도합니다",
        "ACTIVE",
        "RESERVATION_TRANSFER",
        "INAPPROPRIATE",
        "부적절한 사진",
        "PENDING",
        7L,
        "신고자",
        LocalDateTime.of(2026, 7, 14, 9, 0));
  }

  @Test
  @DisplayName("findReports_200과_신고목록을반환한다")
  void findReports_200과_신고목록을반환한다() throws Exception {
    // given
    given(adminPostService.findReports(any(), any()))
        .willReturn(new PageImpl<>(List.of(sampleReport()), PageRequest.of(0, 20), 1));

    // when & then
    mockMvc
        .perform(get("/api/v1/admin/posts/reports"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].reportId").value(10))
        .andExpect(jsonPath("$.data.content[0].postId").value(POST_ID))
        .andExpect(jsonPath("$.data.content[0].reason").value("INAPPROPRIATE"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("findReports_status쿼리파라미터를_서비스로전달한다")
  void findReports_status쿼리파라미터를_서비스로전달한다() throws Exception {
    // given
    given(adminPostService.findReports(any(), any()))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    // when & then
    mockMvc
        .perform(get("/api/v1/admin/posts/reports").param("status", "ACCEPTED"))
        .andExpect(status().isOk());

    verify(adminPostService).findReports(eq(ReportStatus.ACCEPTED), any());
  }

  @Test
  @DisplayName("blindPost_204와_사유를_서비스로전달한다")
  void blindPost_204와_사유를_서비스로전달한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(
            post("/api/v1/admin/posts/{postId}/blind", POST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"음란성 게시물\"}"))
        .andExpect(status().isNoContent());

    verify(adminPostService).blindPost(POST_ID, "음란성 게시물");
  }

  @Test
  @DisplayName("blindPost_사유누락_400과C001")
  void blindPost_사유누락_400() throws Exception {
    // given: @NotBlank 검증 실패 → MethodArgumentNotValidException

    // when & then
    mockMvc
        .perform(
            post("/api/v1/admin/posts/{postId}/blind", POST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  @Test
  @DisplayName("blindPost_없는게시글_404와PO001")
  void blindPost_없는게시글_404() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
        .given(adminPostService)
        .blindPost(POST_ID, "사유");

    // when & then
    mockMvc
        .perform(
            post("/api/v1/admin/posts/{postId}/blind", POST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("blindPost_이미블라인드된글_409와PO004")
  void blindPost_이미블라인드된글_409() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.POST_ALREADY_BLINDED))
        .given(adminPostService)
        .blindPost(POST_ID, "사유");

    // when & then
    mockMvc
        .perform(
            post("/api/v1/admin/posts/{postId}/blind", POST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.POST_ALREADY_BLINDED.getCode()));
  }
}
