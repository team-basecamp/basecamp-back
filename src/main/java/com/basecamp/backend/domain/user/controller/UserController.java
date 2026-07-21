package com.basecamp.backend.domain.user.controller;

import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.post.dto.response.MyPostCursorResponse;
import com.basecamp.backend.domain.post.service.PostService;
import com.basecamp.backend.domain.user.dto.request.UpdateProfileRequest;
import com.basecamp.backend.domain.user.dto.response.MyProfileResponse;
import com.basecamp.backend.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 회원 본인의 프로필 조회 · 수정. {@code /api/v1/users/me} 는 로그인 회원만 접근한다(SecurityConfig). */
@Tag(name = "User", description = "회원 - 내 프로필 조회/수정")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  // "내가 쓴 게시글" 조회는 게시글 도메인 로직이라 PostService에 위임한다.
  // (URL은 users 소유라 이 컨트롤러에 두되, 조회 책임은 post 도메인이 갖는다.)
  private final PostService postService;

  @Operation(summary = "내 프로필 조회", description = "로그인한 회원의 닉네임 · 이메일 · 프로필 이미지 · 권한 등을 조회한다.")
  @GetMapping("/me")
  public ResponseEntity<MyProfileResponse> getMyProfile(@AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(userService.getMyProfile(user.id()));
  }

  @Operation(
      summary = "내 프로필 수정",
      description =
          "닉네임과 프로필 이미지를 수정한다. 요청은 multipart/form-data 로, 회원 정보는 request(JSON) 파트에, "
              + "새 프로필 이미지는 image(파일) 파트에 싣는다. image 를 보내면 그 파일로 교체하고, "
              + "파일 없이 request.removeImage=true 면 이미지를 제거한다. 이메일 · 소셜 제공자 · 권한은 바꿀 수 없다.")
  @PostMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<MyProfileResponse> updateMyProfile(
      @AuthenticationPrincipal AuthUser user,
      @RequestPart("request") @Valid UpdateProfileRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    return ResponseEntity.ok(userService.updateMyProfile(user.id(), request, image));
  }

  // 마이페이지 "내가 쓴 게시글" 목록: 로그인 회원 본인이 작성한 글을 최신순으로 조회한다. (무한 스크롤용, 커서 페이징)
  // 목록 한 줄에는 제목 · 작성일 · 조회수 · 댓글 수만 담는다. 수정/삭제는 각 글의 postId로 기존 게시글 API를 호출한다.
  @Operation(
      summary = "내가 쓴 게시글 목록 조회",
      description =
          "로그인한 회원이 작성한 게시글 목록을 최신순으로 조회한다. "
              + "첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 cursor에 실어 보낸다. "
              + "hasNext가 false면 nextCursor는  null이고 더 조회할 목록이 없다. 삭제·블라인드된 글은 목록에서 제외된다.")
  @GetMapping("/me/posts")
  public ResponseEntity<MyPostCursorResponse> getMyPosts(
      @AuthenticationPrincipal AuthUser user,

      // 직전 응답의 nextCursor를 그대로 돌려보내는 자리. 첫 페이지에서는 생략한다.
      // 값을 직접 만들어 넣지 말 것 — 형식은 서버 구현 세부사항이라 언제든 바뀐다.
      @Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 생략한다.")
          @RequestParam(value = "cursor", required = false)
          String cursor,

      // 페이지당 건수. 상한 검증은 서비스에서 하고 위반 시 400.
      @Parameter(description = "페이지당 건수 (1~50)", example = "10")
          @RequestParam(value = "size", defaultValue = "10")
          int size) {

    return ResponseEntity.ok(postService.getMyPosts(user.id(), cursor, size));
  }
}
