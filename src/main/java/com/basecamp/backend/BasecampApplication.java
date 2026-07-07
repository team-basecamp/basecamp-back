package com.basecamp.backend;

import com.basecamp.backend.domain.camp.repository.CampRepository;
import com.basecamp.backend.domain.camp.service.CampService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BasecampApplication {

	public static void main(String[] args) {
		SpringApplication.run(BasecampApplication.class, args);
	}

	// 앱 최초 실행 시(= camps 테이블이 비어있을 때)에만 고캠핑 API 데이터를 자동으로 DB에 동기화
	// 이미 데이터가 있으면 건너뛰어서, 재시작할 때마다 다시 API를 훑지 않도록 함
	// 실패해도 앱 구동 자체는 막지 않도록 예외를 여기서 처리
	@Bean
	public CommandLineRunner initGocampingData(CampService campService, CampRepository campRepository) {
		return args -> {
			if (campRepository.count() > 0) {
				System.out.println("camps 테이블에 이미 데이터가 있어 초기 동기화를 건너뜁니다.");
				return;
			}
			try {
				campService.fetchAndSaveCampsFromGocampingApi();
			} catch (Exception e) {
				System.out.println(" 앱 시작 시 고캠핑 데이터 동기화 실패 (앱은 정상 구동됩니다): " + e.getMessage());
			}
		};
	}

}
