package com.basecamp.backend.domain.post.dto.response;

// 빌더가 필요 없는가?
// create 시 얘는 필요 없음
// 추가로 필요한게 있을까 response받을 때 그렇다면 create, update, delete, read시 다 다르지 않을까?
public record PostResponse(
        String category,
        String title,
        String content) {
}
