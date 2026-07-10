package com.basecamp.backend.domain.campowner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 캠핑업체 권한 승격 신청. 신청자(회원 id)는 요청 본문이 아니라 access 토큰에서 꺼낸다.
 *
 * <p>여기서는 <b>자릿수·문자 종류만</b> 검증한다(실패 시 400 {@code C001}). 체크섬은 서비스에서 확인하고
 * {@code CO005} 로 응답한다. 실제 사업자 실체 확인은 관리자 심사가 담당한다.</p>
 */
public record CampOwnerApplicationRequest(
		@Schema(description = "사업자등록번호(하이픈 제외 10자리)", example = "2208162517")
		@NotBlank(message = "사업자등록번호는 필수입니다.")
		@Pattern(regexp = "\\d{10}", message = "사업자등록번호는 하이픈 없이 숫자 10자리여야 합니다.")
		String businessNumber,

		@Schema(description = "상호명", example = "베이스캠프 오토캠핑장")
		@NotBlank(message = "상호명은 필수입니다.")
		@Size(max = 100, message = "상호명은 100자를 넘을 수 없습니다.")
		String businessName,

		@Schema(description = "대표자명", example = "홍길동")
		@NotBlank(message = "대표자명은 필수입니다.")
		@Size(max = 50, message = "대표자명은 50자를 넘을 수 없습니다.")
		String representativeName) {
}
