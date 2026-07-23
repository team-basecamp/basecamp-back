package com.basecamp.backend.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.admin.dto.response.BlacklistedUserResponse;
import com.basecamp.backend.domain.admin.service.AdminUserService;
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
 * {@link AdminUserController} 슬라이스 테스트.
 *
 * <p>{@code ROLE_ADMIN} 인가는 {@code SecurityConfig} 의 관심사이므로 {@code addFilters = false} 로 끄고, 요청/응답
 * 조립과 검증만 확인한다.
 */
@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

  private static final Long USER_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminUserService adminUserService;

  @Test
  @DisplayName("blacklistUser_204와_사유를_서비스로전달한다")
  void blacklistUser_204와_사유를_서비스로전달한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(
            post("/api/v1/admin/users/{userId}/blacklist", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"부적절한 게시글 반복 작성\"}"))
        .andExpect(status().isNoContent());

    verify(adminUserService).blacklistUser(USER_ID, "부적절한 게시글 반복 작성");
  }

  @Test
  @DisplayName("blacklistUser_사유누락_400과C001")
  void blacklistUser_사유누락_400() throws Exception {
    // given: @NotBlank 검증 실패 → MethodArgumentNotValidException

    // when & then
    mockMvc
        .perform(
            post("/api/v1/admin/users/{userId}/blacklist", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  @Test
  @DisplayName("blacklistUser_이미제재된회원_409와U003")
  void blacklistUser_이미제재된회원_409() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.USER_ALREADY_BLACKLISTED))
        .given(adminUserService)
        .blacklistUser(USER_ID, "사유");

    // when & then
    mockMvc
        .perform(
            post("/api/v1/admin/users/{userId}/blacklist", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_BLACKLISTED.getCode()));
  }

  @Test
  @DisplayName("findBlacklistedUsers_200과_제재회원목록을반환한다")
  void findBlacklistedUsers_200과_제재회원목록을반환한다() throws Exception {
    // given
    BlacklistedUserResponse response =
        new BlacklistedUserResponse(
            USER_ID,
            "user@example.com",
            "camper",
            "KAKAO",
            "어뷰징",
            LocalDateTime.of(2026, 7, 9, 9, 0));
    given(adminUserService.findBlacklistedUsers(any()))
        .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

    // when & then
    mockMvc
        .perform(get("/api/v1/admin/users/blacklist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].userId").value(USER_ID))
        .andExpect(jsonPath("$.data.content[0].blacklistReason").value("어뷰징"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("releaseUser_204를반환한다")
  void releaseUser_204를반환한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(post("/api/v1/admin/users/{userId}/blacklist/release", USER_ID))
        .andExpect(status().isNoContent());

    verify(adminUserService).releaseUser(USER_ID);
  }

  @Test
  @DisplayName("releaseUser_제재상태가아닌회원_409와U004")
  void releaseUser_제재상태가아닌회원_409() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.USER_NOT_BLACKLISTED))
        .given(adminUserService)
        .releaseUser(USER_ID);

    // when & then
    mockMvc
        .perform(post("/api/v1/admin/users/{userId}/blacklist/release", USER_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_BLACKLISTED.getCode()));
  }
}
