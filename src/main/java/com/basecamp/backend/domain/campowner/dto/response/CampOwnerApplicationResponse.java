package com.basecamp.backend.domain.campowner.dto.response;

import com.basecamp.backend.domain.campowner.entity.CampOwnerApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 캠핑업체 권한 승격 신청 상세. 본인 조회와 관리자 목록이 같은 형태를 쓴다. */
@Schema(description = "캠핑업체 권한 승격 신청")
public record CampOwnerApplicationResponse(
    @Schema(description = "신청 ID", example = "1") Long applicationId,
    @Schema(description = "신청 회원 ID", example = "7") Long userId,
    @Schema(description = "사업자등록번호", example = "1234567890") String businessNumber,
    @Schema(description = "상호명", example = "베이스캠프 오토캠핑장") String businessName,
    @Schema(description = "대표자명", example = "홍길동") String representativeName,
    @Schema(description = "심사 상태", example = "PENDING") String status,
    @Schema(description = "반려 사유(반려된 신청만)", example = "사업자등록증 확인 불가") String rejectReason,
    @Schema(description = "신청 일시") LocalDateTime createdAt,
    @Schema(description = "처리 일시(승인·반려된 신청만)") LocalDateTime processedAt) {

  public static CampOwnerApplicationResponse from(CampOwnerApplication application) {
    return new CampOwnerApplicationResponse(
        application.getId(),
        application.getUserId(),
        application.getBusinessNumber(),
        application.getBusinessName(),
        application.getRepresentativeName(),
        application.getStatus().name(),
        application.getRejectReason(),
        application.getCreatedAt(),
        application.getProcessedAt());
  }
}
