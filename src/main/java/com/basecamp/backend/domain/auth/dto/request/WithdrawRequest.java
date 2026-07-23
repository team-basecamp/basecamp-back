package com.basecamp.backend.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 회원 탈퇴 요청. 사유는 선택 입력이며 {@code users.withdrawal_reason}(VARCHAR 500)에 저장된다. */
public record WithdrawRequest(
    @Schema(description = "탈퇴 사유(선택)", example = "더 이상 이용하지 않아요")
        @Size(max = 500, message = "탈퇴 사유는 500자를 넘을 수 없습니다.")
        String reason) {}
