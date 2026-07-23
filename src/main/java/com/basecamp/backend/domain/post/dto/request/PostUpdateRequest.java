package com.basecamp.backend.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// 게시글 수정 요청 DTO. 컨트롤러에서 @Valid로 검증한 뒤 서비스로 전달한다.
public record PostUpdateRequest(
    // 게시판 카테고리 (GENERAL / CAMP_MATE / RESERVATION_TRANSFER 중 하나)
    @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 30, message = "카테고리는 최대 30자까지 입력 가능합니다.")
        @Pattern(
            regexp = "GENERAL|CAMP_MATE|RESERVATION_TRANSFER",
            message = "카테고리는 GENERAL, CAMP_MATE, RESERVATION_TRANSFER 중 하나여야 합니다.")
        String category,

    // 게시글 제목 (최대 200자)
    @NotBlank(message = "제목은 필수입니다.") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

    // 게시글 본문
    @NotBlank(message = "내용은 필수입니다.") String content,

    // 수정 후에도 남길 "기존" 이미지의 상대경로 목록(예: /images/abc123.jpg).
    // 여기 없는 기존 이미지는 게시글에서 떨어져 나가고 디스크 파일까지 지워진다.
    //   필드 생략(null) : 기존 이미지 전부 유지 (이미지를 건드리지 않는 수정)
    //   빈 배열([])     : 기존 이미지 전부 삭제
    // 새로 올리는 파일은 이 목록이 아니라 multipart 의 images 파트로 보낸다.
    // 아래 제약은 형식만 본다. "이 게시글의 이미지인가"는 PostService.resolveKeptImages 가 판정한다.
    // 최대 개수도 여기서 못 박지 않는다 — 실제 상한은 file.upload.max-count 이고 서비스가 검사한다.
    // max=50 은 그 상한보다 넉넉히 잡은 페이로드 방어선일 뿐이다.
    @Size(max = 50, message = "유지할 이미지는 최대 50개까지 지정할 수 있습니다.")
        List<
                @NotBlank(message = "유지할 이미지 경로는 비어 있을 수 없습니다.")
                // 접두어를 "/images/" 로 못 박지 않는다 — file.upload.url-prefix 로 바뀔 수 있고
                // @Pattern 은 상수라 설정을 못 읽는다. 여기서는 "상대경로 꼴"만 본다:
                // ".." 없이, "/" 로 시작하고, 공백 없는 세그먼트로 이어질 것.
                @Pattern(
                    regexp = "(?!.*\\.\\.)/[^\\s/]+(/[^\\s/]+)*",
                    message = "유지할 이미지 경로는 \"/\" 로 시작하는 상대경로여야 합니다.")
                String>
            keepImageUrls) {}
