package com.basecamp.backend.security.support;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.basecamp.backend.security.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TODO: 임시테스트 - domain/auth 로그인 API 구현되면 이 파일 삭제
@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class TempTokenPrinter implements CommandLineRunner {

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	public void run(String... args) {
		//String token = jwtTokenProvider.createAccessToken(1L, "CUSTOMER");
		//String token = jwtTokenProvider.createAccessToken(1L, "ADMIN");
		String token = jwtTokenProvider.createAccessToken(11L, "CUSTOMER");
		log.info("TEST_ACCESS_TOKEN=Bearer {}", token);
	}

}
