package com.basecamp.backend.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.BusinessException;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.notification.dto.response.NotificationResponse;
import com.basecamp.backend.domain.notification.dto.response.UnreadCountResponse;
import com.basecamp.backend.domain.notification.entity.NotificationTargetType;
import com.basecamp.backend.domain.notification.entity.NotificationType;
import com.basecamp.backend.domain.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link NotificationController} 슬라이스 테스트(#92).
 *
 * <p>SSE push 로직은 {@code NotificationServiceTest} 가 다루고, 여기서는 조회/카운트/읽음 API 의 요청·응답 조립과 예외 매핑만
 * 확인한다. 보안 필터는 이 계층의 관심사가 아니므로 {@code addFilters = false} 로 끄고, {@code @AuthenticationPrincipal} 이
 * 읽을 인증만 직접 세팅한다({@code AuthControllerTest} 와 같은 방식).
 */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long NOTIFICATION_ID = 1L;

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationService notificationService;

  @BeforeEach
  void setUpAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthUser(USER_ID, Role.CUSTOMER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private NotificationResponse sampleResponse() {
    return new NotificationResponse(
        NOTIFICATION_ID,
        NotificationType.RESERVATION_CONFIRMED,
        "'해운대 오토캠핑장' 예약이 확정되었습니다.",
        NotificationTargetType.RESERVATION,
        42L,
        false,
        LocalDateTime.of(2026, 7, 16, 10, 0));
  }

  @Test
  @DisplayName("findMyNotifications_200과_봉투로감싼알림목록을반환한다")
  void findMyNotifications_200과_목록을반환한다() throws Exception {
    // given
    given(notificationService.findMyNotifications(eq(USER_ID), any(), any()))
        .willReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 10), 1));

    // when & then
    mockMvc
        .perform(get("/api/v1/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(NOTIFICATION_ID))
        .andExpect(jsonPath("$.data.content[0].type").value("RESERVATION_CONFIRMED"))
        .andExpect(jsonPath("$.data.content[0].targetType").value("RESERVATION"))
        .andExpect(jsonPath("$.data.content[0].targetId").value(42))
        .andExpect(jsonPath("$.data.content[0].isRead").value(false))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("findMyNotifications_isRead쿼리파라미터를_서비스로전달한다")
  void findMyNotifications_isRead파라미터를_전달한다() throws Exception {
    // given
    given(notificationService.findMyNotifications(eq(USER_ID), eq(false), any()))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    // when & then
    mockMvc
        .perform(get("/api/v1/notifications").param("isRead", "false"))
        .andExpect(status().isOk());

    verify(notificationService).findMyNotifications(eq(USER_ID), eq(false), any());
  }

  @Test
  @DisplayName("getUnreadCount_200과_안읽은개수를반환한다")
  void getUnreadCount_200과_개수를반환한다() throws Exception {
    // given
    given(notificationService.getUnreadCount(USER_ID)).willReturn(UnreadCountResponse.of(3));

    // when & then
    mockMvc
        .perform(get("/api/v1/notifications/unread-count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.count").value(3));
  }

  @Test
  @DisplayName("markAsRead_200과_본인id로_서비스를호출한다")
  void markAsRead_200과_서비스를호출한다() throws Exception {
    // when & then
    mockMvc
        .perform(post("/api/v1/notifications/{id}/read", NOTIFICATION_ID))
        .andExpect(status().isOk());

    verify(notificationService).markAsRead(NOTIFICATION_ID, USER_ID);
  }

  @Test
  @DisplayName("markAsRead_없는알림_404와N001")
  void markAsRead_없는알림_404() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
        .given(notificationService)
        .markAsRead(NOTIFICATION_ID, USER_ID);

    // when & then
    mockMvc
        .perform(post("/api/v1/notifications/{id}/read", NOTIFICATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.NOTIFICATION_NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("markAsRead_타인알림_403과A004")
  void markAsRead_타인알림_403() throws Exception {
    // given
    willThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
        .given(notificationService)
        .markAsRead(NOTIFICATION_ID, USER_ID);

    // when & then
    mockMvc
        .perform(post("/api/v1/notifications/{id}/read", NOTIFICATION_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
  }

  @Test
  @DisplayName("markAllAsRead_200과_서비스를호출한다")
  void markAllAsRead_200과_서비스를호출한다() throws Exception {
    // when & then
    mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isOk());

    verify(notificationService).markAllAsRead(USER_ID);
  }
}
