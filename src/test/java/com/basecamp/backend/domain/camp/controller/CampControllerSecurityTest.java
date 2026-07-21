package com.basecamp.backend.domain.camp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basecamp.backend.common.enums.Role;
import com.basecamp.backend.common.exception.ErrorCode;
import com.basecamp.backend.common.model.AuthUser;
import com.basecamp.backend.domain.camp.entity.Camp;
import com.basecamp.backend.domain.camp.service.CampService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 캠핑장 데이터 주입 엔드포인트({@code POST /camps/fetch}, {@code POST /camps/sync})의 권한 규칙 검증.
 *
 * <p>둘 다 {@code /api/v1/admin} 아래가 아니라 {@code SecurityConfig} 의 URL 규칙으로 묶이지 않는다. 대신 메서드에
 * {@code @PreAuthorize("hasRole('ADMIN')")} 가 붙어 있다.
 *
 * <p><b>{@code @WebMvcTest} 로는 이 테스트를 쓸 수 없다.</b> 컨트롤러 슬라이스는 우리 {@code SecurityConfig} 를 로드하지 않으므로
 * 거기 붙은 {@code @EnableMethodSecurity} 도 동작하지 않고, {@code @PreAuthorize} 가 있든 없든 똑같이 통과한다. SpEL 표현식은
 * 컴파일 검사를 받지 않으므로(오타 시 전부 403) 실제 설정을 띄워 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CampService campService;

  @Test
  @DisplayName("fetch_비로그인_401을반환한다")
  void fetch_비로그인_401을반환한다() throws Exception {
    // given: 토큰 없이 요청한다. anyRequest().authenticated() 가 먼저 막는다.

    // when & then
    mockMvc.perform(post("/api/v1/camps/fetch")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  @DisplayName("fetch_일반회원_403과A004")
  void fetch_일반회원_403과A004() throws Exception {
    // given: 로그인은 했지만 ADMIN 이 아니다. @PreAuthorize 가 막는다.

    // when & then
    mockMvc
        .perform(post("/api/v1/camps/fetch"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
  }

  @Test
  @WithMockUser(roles = "CAMP_OWNER")
  @DisplayName("fetch_캠핑업체_403과A004")
  void fetch_캠핑업체_403과A004() throws Exception {
    // given: 업체 권한으로도 공공데이터 동기화를 트리거할 수 없다.

    // when & then
    mockMvc
        .perform(post("/api/v1/camps/fetch"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("fetch_관리자_200으로통과한다")
  void fetch_관리자_200으로통과한다() throws Exception {
    // given: CampService 는 목이라 외부 고캠핑 API 를 호출하지 않는다.

    // when & then
    mockMvc.perform(post("/api/v1/camps/fetch")).andExpect(status().isOk());
  }

  // --- POST /camps/sync : 요청 본문(캠핑장 배열)을 받는다. 본문 파싱이 먼저 일어나므로 빈 배열을 함께 보낸다. ---

  @Test
  @DisplayName("sync_비로그인_401을반환한다")
  void sync_비로그인_401을반환한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(post("/api/v1/camps/sync").contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  @DisplayName("sync_일반회원_403과A004")
  void sync_일반회원_403과A004() throws Exception {
    // given: 로그인만으로 외부 데이터를 DB 에 밀어넣을 수 있으면 안 된다.

    // when & then
    mockMvc
        .perform(post("/api/v1/camps/sync").contentType(MediaType.APPLICATION_JSON).content("[]"))
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
    mockMvc
        .perform(post("/api/v1/camps/sync").contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("my_비로그인_401을반환한다")
  void my_비로그인_401을반환한다() throws Exception {
    // given: "내 캠핑장"은 GET /api/v1/camps/** 공개 규칙 아래에 있다. SecurityConfig 가 그보다 먼저
    //        /camps/my 를 authenticated() 로 잡아주기 때문에만 보호된다(먼저 매칭된 규칙이 이긴다).
    //        규칙 순서가 뒤바뀌면 @PreAuthorize 가 대신 막아 401 이 아니라 403 이 나가므로 이 테스트가 깨진다.

    // when & then
    mockMvc.perform(get("/api/v1/camps/my")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("my_일반회원_403과A004")
  void my_일반회원_403과A004() throws Exception {
    // given: 캠핑장을 등록할 수 있는 건 CAMP_OWNER 뿐이므로 조회 권한도 같아야 한다.

    // when & then
    mockMvc
        .perform(get("/api/v1/camps/my").with(as(OWNER_ID, Role.CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

    verify(campService, never()).getMyCamps(any());
  }

  @Test
  @DisplayName("my_캠핑업체_200과_토큰의회원id로조회한다")
  void my_캠핑업체_200으로통과한다() throws Exception {
    // given
    given(campService.getMyCamps(OWNER_ID)).willReturn(List.of());

    // when & then
    mockMvc
        .perform(get("/api/v1/camps/my").with(as(OWNER_ID, Role.CAMP_OWNER)))
        .andExpect(status().isOk());

    // 조회 대상은 요청 파라미터가 아니라 access 토큰에서 꺼낸 회원 id 여야 한다.
    verify(campService).getMyCamps(OWNER_ID);
  }

  // --- POST /camps/register : 업체가 직접 등록하는 캠핑장. CAMP_OWNER 만 허용한다(#53). ---

  private static final Long OWNER_ID = 7L;

  /** 유효한 요청 본문. @Valid 는 @PreAuthorize 보다 먼저 돌므로, 400 이 아니라 403 을 보려면 본문이 유효해야 한다. */
  private static final String VALID_CAMP_JSON =
      """
			{"facltNm":"베이스캠프 오토캠핑장","addr1":"강원도 춘천시 어디로 1","tel":"033-123-4567",
			 "induty":"오토캠핑","price":50000}
			""";

  /**
   * 등록은 multipart/form-data 다. 캠핑장 정보는 {@code request} 라는 JSON 파트로 실린다(컨트롤러의
   * {@code @RequestPart("request")}). 이미지 파트는 선택이라 여기선 붙이지 않는다.
   */
  private MockMultipartFile validRequestPart() {
    return new MockMultipartFile(
        "request",
        "",
        MediaType.APPLICATION_JSON_VALUE,
        VALID_CAMP_JSON.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * {@code @WithMockUser} 는 principal 로 스프링의 {@code User} 를 넣는다. 그러면 컨트롤러의
   * {@code @AuthenticationPrincipal AuthUser} 가 조용히 {@code null} 이 되어 NPE 로 실패한다. 인가 통과 경로를 검증하려면
   * 실제 principal 타입({@link AuthUser})을 넣어야 한다.
   */
  private RequestPostProcessor as(Long userId, Role role) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AuthUser(userId, role),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  @Test
  @DisplayName("register_비로그인_401을반환한다")
  void register_비로그인_401을반환한다() throws Exception {
    // given & when & then
    mockMvc
        .perform(multipart("/api/v1/camps/register").file(validRequestPart()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("register_일반회원_403과A004")
  void register_일반회원_403과A004() throws Exception {
    // given: 로그인만으로 캠핑장을 등록할 수 있으면 안 된다. 업체 승격(#53)을 거쳐야 한다.

    // when & then
    mockMvc
        .perform(
            multipart("/api/v1/camps/register")
                .file(validRequestPart())
                .with(as(OWNER_ID, Role.CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

    verify(campService, never()).registerCamp(any(), any(), any());
  }

  @Test
  @DisplayName("register_관리자_403과A004")
  void register_관리자_403과A004() throws Exception {
    // given: 관리자도 등록할 수 없다. camps.owner_id 는 실제 업체를 가리켜야 하고, 관리자 계정이 소유자가 되면 안 된다.

    // when & then
    mockMvc
        .perform(
            multipart("/api/v1/camps/register").file(validRequestPart()).with(as(9L, Role.ADMIN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

    verify(campService, never()).registerCamp(any(), any(), any());
  }

  @Test
  @DisplayName("register_캠핑업체_201과_토큰의회원id를owner로넘긴다")
  void register_캠핑업체_201로통과한다() throws Exception {
    // given
    given(campService.registerCamp(any(), eq(OWNER_ID), any()))
        .willReturn(Camp.builder().facltNm("베이스캠프 오토캠핑장").addr1("강원도 춘천시 어디로 1").build());

    // when & then
    mockMvc
        .perform(
            multipart("/api/v1/camps/register")
                .file(validRequestPart())
                .with(as(OWNER_ID, Role.CAMP_OWNER)))
        .andExpect(status().isCreated());

    // 소유자는 요청 본문이 아니라 access 토큰에서 꺼낸 회원 id 여야 한다.
    verify(campService).registerCamp(any(), eq(OWNER_ID), any());
  }
}
