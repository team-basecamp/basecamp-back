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
                message = "카테고리는 GENERAL, CAMP_MATE, RESERVATION_TRANSFER 중 하나여야 합니다."
        )
        String category,

        // 게시글 제목 (최대 200자)
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
        String title,

        // 게시글 본문
        @NotBlank(message = "내용은 필수입니다.")
        String content,

        // 수정 후에도 남길 "기존" 이미지의 상대경로 목록(예: /images/abc123.jpg).
        // 여기 없는 기존 이미지는 게시글에서 떨어져 나가고 디스크 파일까지 지워진다.
        //   필드 생략(null) : 기존 이미지 전부 유지 (이미지를 건드리지 않는 수정)
        //   빈 배열([])     : 기존 이미지 전부 삭제
        // 새로 올리는 파일은 이 목록이 아니라 multipart 의 images 파트로 보낸다.
        List<String> keepImageUrls) {
}
