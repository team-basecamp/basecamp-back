package com.basecamp.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class FileStoragePropertiesTest {

	private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

	@AfterAll
	static void closeValidatorFactory() {
		VALIDATOR_FACTORY.close();
	}

	private Set<?> violations(String urlPrefix) {
		FileStorageProperties properties = new FileStorageProperties();
		properties.setUrlPrefix(urlPrefix);
		return VALIDATOR.validateProperty(properties, "urlPrefix");
	}

	@Test
	void setUrlPrefix_끝에슬래시가있으면_떼어내고보관한다() {
		// given
		FileStorageProperties properties = new FileStorageProperties();

		// when
		properties.setUrlPrefix("/images//");

		// then
		assertThat(properties.getUrlPrefix()).isEqualTo("/images");
		assertThat(properties.getUrlPrefixPattern()).isEqualTo("/images/**");
		assertThat(properties.getUrlPrefixPath()).isEqualTo("/images/");
	}

	@Test
	void setUrlPrefix_앞뒤공백은_제거된다() {
		// given
		FileStorageProperties properties = new FileStorageProperties();

		// when
		properties.setUrlPrefix("  /images/  ");

		// then
		assertThat(properties.getUrlPrefix()).isEqualTo("/images");
	}

	@Test
	void 검증_기본값은_통과한다() {
		// given
		FileStorageProperties properties = new FileStorageProperties();

		// when & then
		assertThat(VALIDATOR.validateProperty(properties, "urlPrefix")).isEmpty();
	}

	@Test
	void 검증_루트접두어는_거부된다() {
		// 루트를 허용하면 매처가 "/**" 가 되어 API 전체가 인증 없이 열린다.
		assertThat(violations("/")).isNotEmpty();
	}

	@Test
	void 검증_비었거나_상대경로거나_공백포함이면_거부된다() {
		assertThat(violations("")).isNotEmpty();
		assertThat(violations("   ")).isNotEmpty();
		assertThat(violations("images")).isNotEmpty();
		assertThat(violations("/im ages")).isNotEmpty();
		assertThat(violations("//images")).isNotEmpty();
	}

	@Test
	void 검증_중첩된_절대경로는_통과한다() {
		assertThat(violations("/static/images")).isEmpty();
	}
}
