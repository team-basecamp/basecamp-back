package com.basecamp.backend.domain.campowner.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 사업자등록번호 체크섬 검증. 규칙이 국세청 표준이므로 실제로 유효한 번호로 고정한다.
 */
class BusinessNumberValidatorTest {

	@ParameterizedTest
	@ValueSource(strings = {"2208162517", "1208147521"})
	@DisplayName("isValid_체크섬이맞는번호_true")
	void isValid_체크섬이맞는번호_true(String businessNumber) {
		// given & when & then
		assertThat(BusinessNumberValidator.isValid(businessNumber)).isTrue();
	}

	@Test
	@DisplayName("isValid_체크섬이틀린번호_false")
	void isValid_체크섬이틀린번호_false() {
		// given: 마지막 검증 숫자만 1 틀리다(2208162517 이 유효한 번호다).

		// when & then
		assertThat(BusinessNumberValidator.isValid("2208162518")).isFalse();
	}

	@Test
	@DisplayName("isValid_1234567890_false")
	void isValid_1234567890_false() {
		// given: 흔히 예시로 쓰는 번호지만 체크섬을 통과하지 못한다. 문서·Swagger 예시로 쓰면 안 된다.

		// when & then
		assertThat(BusinessNumberValidator.isValid("1234567890")).isFalse();
	}

	@Test
	@DisplayName("isValid_모두0인번호_체크섬은통과하지만_false")
	void isValid_모두0인번호_false() {
		// given: 0000000000 은 가중치 합이 0 이라 체크섬을 통과한다. 세무서 코드가 00 인 사업자는 없으므로 별도로 막는다.

		// when & then
		assertThat(BusinessNumberValidator.isValid("0000000000")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"220816251", "22081625177", "220-81-6251", "22081625aa"})
	@DisplayName("isValid_길이나문자가어긋난번호_false")
	void isValid_길이나문자가어긋난번호_false(String businessNumber) {
		// given & when & then
		assertThat(BusinessNumberValidator.isValid(businessNumber)).isFalse();
	}

	@Test
	@DisplayName("isValid_null_false")
	void isValid_null_false() {
		// given & when & then
		assertThat(BusinessNumberValidator.isValid(null)).isFalse();
	}

}
