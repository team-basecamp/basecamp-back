package com.basecamp.backend.domain.campowner.service;

/**
 * 사업자등록번호(10자리) 형식 검증.
 *
 * <p>국세청이 정한 검증 규칙이다. 앞 9자리에 가중치 {@code 1,3,7,1,3,7,1,3,5} 를 곱해 더하고,
 * 9번째 자리에 5를 곱한 값의 십의 자리를 한 번 더 더한 뒤, 10에서 그 합의 일의 자리를 뺀 값이
 * 마지막 자리(검증 숫자)와 같아야 한다.</p>
 *
 * <p><b>이건 "숫자가 규칙에 맞는가"만 본다.</b> 실제로 존재하고 휴·폐업이 아닌 사업자인지는 국세청 진위 확인 API 로만
 * 알 수 있고, 그 연동은 이번 범위 밖이다(#53). 즉 여기를 통과해도 실체가 보장되지 않으므로, 최종 판단은
 * 관리자 심사가 한다.</p>
 */
final class BusinessNumberValidator {

	private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};
	private static final int LENGTH = 10;

	private BusinessNumberValidator() {
	}

	/** @param businessNumber 하이픈을 제외한 10자리 숫자 문자열 */
	static boolean isValid(String businessNumber) {
		if (businessNumber == null || businessNumber.length() != LENGTH) {
			return false;
		}
		int[] digits = new int[LENGTH];
		boolean allZero = true;
		for (int i = 0; i < LENGTH; i++) {
			char c = businessNumber.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
			digits[i] = c - '0';
			allZero &= digits[i] == 0;
		}
		// "0000000000" 은 체크섬을 통과한다. 앞 두 자리는 세무서 코드라 00 인 사업자는 존재하지 않으므로 걸러낸다.
		if (allZero) {
			return false;
		}

		int sum = 0;
		for (int i = 0; i < WEIGHTS.length; i++) {
			sum += digits[i] * WEIGHTS[i];
		}
		// 9번째 자리(index 8)에 5를 곱한 값의 십의 자리를 한 번 더 더한다.
		sum += (digits[8] * 5) / 10;

		int checkDigit = (10 - (sum % 10)) % 10;
		return checkDigit == digits[LENGTH - 1];
	}

}
