package com.basecamp.backend.domain.camp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.domain.camp.service.CampService;

/**
 * 캠핑장 데이터 주입 엔드포인트({@code POST /camps/fetch}, {@code POST /camps/sync})의 권한 규칙 검증.
 *
 * <p>둘 다 {@code /api/v1/admin} 아래가 아니라 {@code SecurityConfig} 의 URL 규칙으로 묶이지 않는다.
 * 대신 메서드에 {@code @PreAuthorize("hasRole('ADMIN')")} 가 붙어 있다.</p>
 *
 * <p><b>{@code @WebMvcTest} 로는 이 테스트를 쓸 수 없다.</b> 컨트롤러 슬라이스는 우리 {@code SecurityConfig} 를
 * 로드하지 않으므로 거기 붙은 {@code @EnableMethodSecurity} 도 동작하지 않고, {@code @PreAuthorize} 가
 * 있든 없든 똑같이 통과한다. SpEL 표현식은 컴파일 검사를 받지 않으므로(오타 시 전부 403) 실제 설정을 띄워 확인한다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CampService campService;

	@Test
	@DisplayName("fetch_비로그인_401을반환한다")
	void fetch_비로그인_401을반환한다() throws Exception {
		// given: 토큰 없이 요청한다. anyRequest().authenticated() 가 먼저 막는다.

		// when & then
		mockMvc.perform(post("/api/v1/camps/fetch"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	@DisplayName("fetch_일반회원_403과A004")
	void fetch_일반회원_403과A004() throws Exception {
		// given: 로그인은 했지만 ADMIN 이 아니다. @PreAuthorize 가 막는다.

		// when & then
		mockMvc.perform(post("/api/v1/camps/fetch"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
	}

	@Test
	@WithMockUser(roles = "CAMP_OWNER")
	@DisplayName("fetch_캠핑업체_403과A004")
	void fetch_캠핑업체_403과A004() throws Exception {
		// given: 업체 권한으로도 공공데이터 동기화를 트리거할 수 없다.

		// when & then
		mockMvc.perform(post("/api/v1/camps/fetch"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("fetch_관리자_200으로통과한다")
	void fetch_관리자_200으로통과한다() throws Exception {
		// given: CampService 는 목이라 외부 고캠핑 API 를 호출하지 않는다.

		// when & then
		mockMvc.perform(post("/api/v1/camps/fetch"))
				.andExpect(status().isOk());
	}

	// --- POST /camps/sync : 요청 본문(캠핑장 배열)을 받는다. 본문 파싱이 먼저 일어나므로 빈 배열을 함께 보낸다. ---

	@Test
	@DisplayName("sync_비로그인_401을반환한다")
	void sync_비로그인_401을반환한다() throws Exception {
		// given & when & then
		mockMvc.perform(post("/api/v1/camps/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content("[]"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	@DisplayName("sync_일반회원_403과A004")
	void sync_일반회원_403과A004() throws Exception {
		// given: 로그인만으로 외부 데이터를 DB 에 밀어넣을 수 있으면 안 된다.

		// when & then
		mockMvc.perform(post("/api/v1/camps/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content("[]"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

		// 권한이 없으면 서비스는 아예 호출되지 않아야 한다.
		verify(campService, never()).saveCampsFromApi(any());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("sync_관리자_200으로통과한다")
	void sync_관리자_200으로통과한다() throws Exception {
		// given & when & then
		mockMvc.perform(post("/api/v1/camps/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.content("[]"))
				.andExpect(status().isOk());
	}

}
