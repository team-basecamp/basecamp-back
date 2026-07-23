package com.basecamp.backend.domain.notification.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.notification.dto.response.NotificationResponse;
import com.basecamp.backend.domain.notification.dto.response.UnreadCountResponse;
import com.basecamp.backend.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @Operation(
      summary = "알림 구독(SSE)",
      description =
          "로그인한 사용자의 실시간 알림 스트림을 연다. 새 알림이 발생하면 `notification` 이벤트로 push 된다. "
              + "브라우저 EventSource 는 헤더를 실을 수 없으므로 프론트는 폴리필 등으로 Authorization 헤더를 전달해야 한다.")
  @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(@AuthenticationPrincipal AuthUser user) {
    return notificationService.subscribe(user.id());
  }

  @Operation(
      summary = "내 알림 목록 조회",
      description = "로그인한 사용자의 알림을 최신순으로 페이지네이션 조회한다. `isRead` 로 읽음/안읽음 필터링이 가능하다.")
  @GetMapping
  public ResponseEntity<Page<NotificationResponse>> findMyNotifications(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(required = false) Boolean isRead,
      @ParameterObject
          @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(notificationService.findMyNotifications(user.id(), isRead, pageable));
  }

  @Operation(summary = "안읽은 알림 개수 조회", description = "헤더 뱃지 표시용. 안읽은 알림 개수를 반환한다.")
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> getUnreadCount(
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(notificationService.getUnreadCount(user.id()));
  }

  @Operation(summary = "개별 알림 읽음 처리", description = "본인 알림 하나를 읽음 처리한다. 이미 읽었어도 정상 처리된다(멱등).")
  @PostMapping("/{notificationId}/read")
  public ResponseEntity<Void> markAsRead(
      @PathVariable Long notificationId, @AuthenticationPrincipal AuthUser user) {
    notificationService.markAsRead(notificationId, user.id());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "전체 알림 읽음 처리", description = "내 안읽은 알림을 모두 읽음 처리한다.")
  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AuthUser user) {
    notificationService.markAllAsRead(user.id());
    return ResponseEntity.ok().build();
  }
}
