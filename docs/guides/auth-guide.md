# 로그인이 필요한 API 만들기 가이드

> Spring Security 설정이 끝났습니다. **여러분이 할 일은 거의 없습니다.** 이 문서는 "그럼 내 API에서 로그인한 사람이 누군지 어떻게 아나요?"에 답합니다.

## 목차

1. [결론부터 — 3줄 요약](#1-결론부터--3줄-요약)
2. [로그인한 회원 정보 꺼내기](#2-로그인한-회원-정보-꺼내기)
3. [무슨 일이 일어나고 있나](#3-무슨-일이-일어나고-있나)
4. [내 API를 로그인 필수로 만들려면?](#4-내-api를-로그인-필수로-만들려면)
5. [특정 권한만 허용하기](#5-특정-권한만-허용하기)
6. [인증에 실패하면 어떤 응답이 나가나](#6-인증에-실패하면-어떤-응답이-나가나)
7. [Swagger에서 테스트하기](#7-swagger에서-테스트하기)
8. [테스트 코드 작성하기](#8-테스트-코드-작성하기)
9. [자주 하는 실수](#9-자주-하는-실수)
10. [체크리스트](#10-체크리스트)

---

## 1. 결론부터 — 3줄 요약

1. **새로 만드는 API는 기본적으로 로그인이 필요합니다.** 여러분이 설정할 게 없습니다.
2. 컨트롤러 메서드에 **`@AuthenticationPrincipal AuthUser user`** 파라미터를 추가하면 로그인한 회원 정보가 들어옵니다. 회원 id는 `user.id()`.
3. **회원 id를 요청 본문(body)이나 쿼리 파라미터로 받지 마세요.** 그건 보안 사고입니다. ([9절](#9-자주-하는-실수) 참고)

```java
@PostMapping("/api/v1/posts")
public ResponseEntity<PostDetailResponse> createPost(
        @AuthenticationPrincipal AuthUser user,          // ← 이 한 줄이 전부입니다
        @RequestBody @Valid PostCreateRequest request) {
    PostDetailResponse response = postService.createPost(user.id(), ...);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

---

## 2. 로그인한 회원 정보 꺼내기

### 기본형

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.basecamp.backend.common.model.AuthUser;

@GetMapping("/api/v1/reservations/me")
public ResponseEntity<List<ReservationResponse>> findMyReservations(
        @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(reservationService.findByUserId(user.id()));
}
```

`AuthUser`는 필드가 둘뿐인 아주 단순한 record입니다.

```java
public record AuthUser(Long id, Role role) {}
```

| 꺼내는 법 | 값 |
|---|---|
| `user.id()` | `users` 테이블의 `user_id` (예: `7L`) |
| `user.role()` | `Role.CUSTOMER` / `Role.CAMP_OWNER` / `Role.ADMIN` |

대부분은 `user.id()`만 쓰면 됩니다. 바로 서비스로 넘기세요.

### 이건 어디서 오나요?

인증 필터(`JwtAuthenticationFilter`)가 토큰을 검증한 뒤 `AuthUser`를 만들어 넣어둡니다.

```java
// JwtAuthenticationFilter.java — 여러분이 건드릴 일은 없습니다
UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        new AuthUser(userId, roleValue),                     // ← principal (여기!)
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + roleValue.name())));
SecurityContextHolder.getContext().setAuthentication(authentication);
```

`@AuthenticationPrincipal`은 저 **첫 번째 인자(principal)** 를 그대로 꺼내주는 애너테이션입니다. 그래서 파라미터 타입이 `AuthUser`여야 합니다.

> ⚠️ **타입을 틀리면 조용히 `null`이 들어옵니다.** `@AuthenticationPrincipal Long userId`라고 쓰면 예외가 나지 않고 `userId`가 그냥 `null`이 됩니다. 컴파일도 되고 실행도 되다가, 한참 뒤 엉뚱한 곳에서 `NullPointerException`으로 터집니다. **반드시 `AuthUser`로 받으세요.**

### 예전에는 `Long userId`였습니다 — 왜 바꿨나

혹시 예전 코드나 옛날 PR에서 이런 걸 보셨다면, **지금은 쓰지 않습니다.**

```java
@AuthenticationPrincipal Long userId    // 예전 방식. 더 이상 동작하지 않습니다
```

바꾼 이유가 셋 있습니다.

**1. 신원(identity)이 조용히 `null`이 되는 게 위험했습니다.**

`@AuthenticationPrincipal`은 타입이 안 맞아도 예외를 던지지 않고 `null`을 넘깁니다. principal이 `Long`이던 시절, 누군가 필터에서 principal 타입을 바꾸면 **모든 컨트롤러의 `userId`가 조용히 `null`이 되고** 컴파일도 테스트도 통과합니다. 그 `null`이 `findByUserId(null)` 같은 쿼리로 흘러 들어갑니다. 터지면 다행이고, 안 터지면 더 나쁩니다.

타입을 `AuthUser` 하나로 고정해두면 "이게 principal이다"가 코드에 명시적으로 박힙니다. (그래도 타입을 틀리면 여전히 `null`입니다. 위 경고를 보세요.)

**2. `role`을 꺼낼 수 없었습니다.**

`Long`에는 회원 id밖에 없습니다. 권한은 authorities에만 들어 있어서, 컨트롤러에서 `role`을 알려면 `Authentication` 객체를 통째로 꺼내 뒤져야 했습니다. `CAMP_OWNER`가 들어오면 이런 요구가 늘어납니다. 지금은 `user.role()`이면 끝입니다.

**3. 담을 값이 늘 때마다 모든 컨트롤러를 고쳐야 했습니다.**

`AuthUser`에 필드를 하나 추가하면 컨트롤러 시그니처는 그대로입니다.

### 덤 — 알 수 없는 `role`을 이제 거부합니다

principal을 `Role` enum으로 좁히면서, 필터가 토큰의 role 문자열을 `Role.valueOf()`로 변환하게 됐습니다. 그래서 role이 `"HACKER"` 같은 값이면 **401로 거부**됩니다.

예전에는 `"ROLE_" + role`을 그대로 권한으로 부여했기 때문에 `ROLE_HACKER`라는 권한이 만들어지고, `anyRequest().authenticated()`만 걸린 API는 그대로 통과했습니다. 그런 토큰을 만들려면 우리 서명 키가 필요하니 실제로 뚫린 적은 없지만, **우리 코드가 role을 잘못 넘기는 버그**가 조용히 넘어가지 않게 됐습니다.

> `AuthUser`가 `security`가 아니라 `common/model`에 있는 이유도 같은 맥락입니다. 로그인이 필요한 모든 컨트롤러가 이 타입을 참조하는데, `security`에 두면 `domain` 전체가 `security`를 의존하게 됩니다. 로직 없는 값 타입이라 공용 패키지로 내려뒀습니다.

### 왜 `UserDetails`가 아닌가요?

스프링 시큐리티 예제에서 자주 보이는 `UserDetails`는 **아이디/비밀번호로 DB를 조회해 인증하는 방식**(`DaoAuthenticationProvider`)을 위한 인터페이스입니다.

우리는 소셜 로그인 + JWT라 그 방식을 **아예 쓰지 않습니다.** 그래서 `UserDetails`를 구현하면 `getPassword()`, `isAccountNonExpired()`처럼 **아무도 호출하지 않을 메서드 6개**를 억지로 채워야 합니다. 그럴 이유가 없어서 그냥 record를 씁니다.

### 회원 정보(닉네임, 이메일)가 더 필요하면?

`AuthUser`에는 `id`와 `role`뿐입니다. 나머지는 **서비스에서 조회**하세요.

```java
// Service
User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
```

토큰에 닉네임을 담지 않는 이유는, 토큰이 한 번 발급되면 30분간 바뀌지 않기 때문입니다. 닉네임을 바꿔도 30분 동안 옛 값이 따라다니게 됩니다. **바뀔 수 있는 값은 토큰에 담지 않습니다.**

---

## 3. 무슨 일이 일어나고 있나

프론트엔드가 API를 부를 때 헤더에 토큰을 실어 보냅니다.

```
POST /api/v1/posts
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6...
```

그러면 요청이 컨트롤러에 닿기 전에 이런 일이 벌어집니다.

```
요청 도착
   │
   ▼
[JwtAuthenticationFilter]  ← Authorization 헤더에서 토큰을 꺼내 검사
   │
   ├─ 토큰 없음/만료/위조 ────────┐
   ├─ 로그아웃된 토큰 (Redis)      │  인증을 세팅하지 않고 그냥 통과
   ├─ 제재된 회원 (Redis)          │  (여기서 예외를 던지지 않습니다)
   │                              │
   └─ 전부 통과 → SecurityContext에 AuthUser(id, role) 저장
   │                              │
   ▼                              ▼
[authorizeHttpRequests]  ← "이 URL이 인증을 요구하나?" 확인
   │                              │
   │                              └─→ 인증 없음 → 401/403 JSON 응답 후 종료
   ▼
[내 컨트롤러]  @AuthenticationPrincipal AuthUser user ← 여기로 배달됨
```

**중요한 포인트 두 가지:**

1. 필터는 토큰이 이상해도 **예외를 던지지 않고 그냥 지나갑니다.** 인증을 세팅하지 않을 뿐입니다. 실제로 막는 건 그다음 단계입니다.
2. 그래서 컨트롤러까지 도달했다면 **`user`는 이미 검증이 끝난 값**입니다. 여러분이 다시 확인할 필요가 없습니다.

---

## 4. 내 API를 로그인 필수로 만들려면?

**아무것도 안 하면 됩니다.** `SecurityConfig`의 마지막 줄이 이렇게 되어 있습니다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/camps/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/camps/fetch").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())          // ← 나머지 전부 로그인 필수
```

`anyRequest().authenticated()` — **명시적으로 열어둔 것 말고는 전부 로그인이 필요합니다.** 새 API를 만들면 자동으로 보호됩니다.

### 지금 열려 있는(로그인 없이 되는) 경로

| 경로 | 이유 |
|---|---|
| `POST /api/v1/auth/login/**` | 로그인하려면 로그인이 필요할 수 없으니까 |
| `POST /api/v1/auth/token/refresh` | access 토큰이 만료된 상태에서 부르는 API |
| `GET /api/v1/camps/**` | 비로그인 방문자도 캠핑장을 둘러볼 수 있어야 함 |
| `/swagger-ui/**`, `/v3/api-docs/**` | API 문서 |

> `GET`만 열려 있습니다. 같은 경로 아래여도 `GET`이 아니면 로그인이 필요합니다. 공공데이터를 DB에 밀어넣는 `POST /api/v1/camps/fetch`와 `POST /api/v1/camps/sync`는 추가로 `@PreAuthorize("hasRole('ADMIN')")`가 걸려 있습니다.

### 내 API를 공개(비로그인 허용)하고 싶다면

`SecurityConfig`에 한 줄 추가해야 합니다. **혼자 고치지 말고 PR에서 리뷰를 받으세요.** 실수로 열면 인증이 통째로 무력화됩니다.

```java
.requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
```

> **주의**: 공개 경로에서 `@AuthenticationPrincipal AuthUser user`를 받으면 로그인한 사람은 값이 들어오고, 비로그인 방문자는 **`null`이 들어옵니다.** "로그인했으면 찜 여부도 같이 내려주기" 같은 기능에 쓸 수 있지만, `null` 처리를 반드시 해야 합니다.
>
> ```java
> if (user != null) { ... }
> ```
>
> 로그인 필수 경로에서는 `null`일 수 없으니 검사하지 마세요. 불필요한 코드입니다.

---

## 5. 특정 권한만 허용하기

권한은 셋입니다: `CUSTOMER`(일반 회원), `CAMP_OWNER`(캠핑업체), `ADMIN`(관리자).

스프링 시큐리티는 인가(authorization)를 **두 층위**로 제공합니다. 둘 다 표준이고, 실무에서는 보통 섞어 씁니다.

| | 요청 단위 (`SecurityConfig`) | 메서드 단위 (`@PreAuthorize`) |
|---|---|---|
| 어디에 쓰나 | `/api/v1/admin/**` 처럼 **경로 접두사로 뭉뚱그려** 막을 때 | **특정 메서드 하나**에만 걸리는 규칙 |
| 규칙 위치 | `SecurityConfig` 한 곳 (전체 감사 쉬움) | 보호할 메서드 바로 위 (누락 발견 쉬움) |
| 경로를 바꾸면 | ⚠️ **조용히 매칭 실패** | 영향 없음 |
| 메서드 인자 참조 | 불가 | 가능 (SpEL) |

**우리 프로젝트는 둘 다 켜져 있습니다.** 어느 쪽을 쓸지는 규칙의 성격으로 정하세요.

### 굵은 규칙 — `SecurityConfig`에서 URL 단위로

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
```

관리자 API는 URL을 `/api/v1/admin/`으로 시작하게만 만들면 **이미 보호됩니다.** 추가 설정이 필요 없습니다.

> `hasRole("ADMIN")`은 실제로는 `ROLE_ADMIN` 권한을 확인합니다. 필터가 `"ROLE_" + role`로 만들어 넣어주기 때문에 접두사를 직접 쓰지 않습니다.

> ⚠️ **경로 기반의 함정**: 누군가 `/api/v1/admin/users`를 `/api/v1/administration/users`로 바꾸면, `requestMatchers("/api/v1/admin/**")`는 **아무 경고 없이 매칭을 멈춥니다.** 컴파일도 테스트도 통과하고, 관리자 API가 열립니다. 컨트롤러 경로를 바꿀 때는 `SecurityConfig`를 반드시 같이 확인하세요.

### 세밀한 규칙 — 메서드에 `@PreAuthorize`

경로 접두사로 묶이지 않는 규칙은 메서드에 직접 붙입니다. 실제 사용 예시가 `CampController`에 있습니다.

```java
// 공공데이터 동기화 — 관리자만. 경로가 /api/v1/admin 아래가 아니라 URL 규칙으로 묶이지 않는다.
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/fetch")
public ResponseEntity<String> fetchCamps() { ... }
```

규칙이 보호 대상 옆에 있어서, 경로를 바꿔도 따라옵니다. 여러 권한을 허용하려면 `hasAnyRole('CAMP_OWNER', 'ADMIN')`.

주의할 점이 셋 있습니다.

1. **안 붙이면 그냥 통과합니다.** `SecurityConfig`의 `anyRequest().authenticated()`가 "로그인은 했는지"까지만 봅니다. 권한 제한이 필요한데 깜빡하면 아무도 안 막아줍니다.
2. **SpEL은 문자열이라 컴파일 검사를 안 받습니다.** `hasRole('CAMPOWNER')`처럼 오타를 내도 빌드가 통과하고, 런타임에 **모두 403**이 됩니다. 반드시 테스트로 확인하세요.
3. **같은 클래스 안에서 자기 메서드를 호출하면 무시됩니다.** 프록시 기반이라 그렇습니다(self-invocation).

### 컨트롤러에서 권한을 알아야 한다면

`user.role()`로 꺼낼 수 있습니다. 다만 **"이 API는 ADMIN만"** 같은 접근 제어는 `SecurityConfig`에 맡기고, `role()`은 "관리자면 응답에 필드를 더 담아준다" 같은 **분기**에만 쓰세요.

```java
if (user.role() == Role.ADMIN) { ... }
```

### 서비스에서 "내 것인지" 확인하기

권한(ROLE)과 **소유권**은 다릅니다. `CUSTOMER` 아무나 남의 게시글을 지울 수 있으면 안 되겠죠. 이건 Security가 아니라 **서비스 로직**에서 확인합니다.

```java
// PostService
public void deletePost(Long userId, Long postId) {
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

    if (!post.getUserId().equals(userId)) {          // 내 글이 아니면 거부
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    postRepository.delete(post);
}
```

> 도메인 전용 에러 코드(`P001` 같은)가 필요하면 `ErrorCode` enum에 추가하세요. 여기서는 공통 코드인 `ENTITY_NOT_FOUND`(`C003`)를 썼습니다.

---

## 6. 인증에 실패하면 어떤 응답이 나가나

여러분이 처리할 필요 없습니다. 아래 JSON이 자동으로 나갑니다.

```json
{
  "code": "A003",
  "message": "만료된 토큰입니다.",
  "status": 401,
  "errors": []
}
```

| 상황 | HTTP | code |
|---|---|---|
| `Authorization` 헤더가 없음 | 401 | `A001` |
| 토큰이 위조·형식 오류 | 401 | `A002` |
| 토큰 만료 (30분 지남) | 401 | `A003` |
| 로그아웃/탈퇴한 토큰 | 401 | `A002` |
| refresh 토큰으로 API 호출 | 401 | `A002` |
| 관리자에게 제재된 회원 | 403 | `A007` |
| 로그인은 했는데 권한 부족 (ADMIN 아님) | 403 | `A004` |

**401과 403의 차이**: 401은 "당신이 누군지 모르겠다"(로그인하세요), 403은 "누군지는 알겠는데 권한이 없다"입니다.

프론트엔드는 **401을 받으면** `POST /api/v1/auth/token/refresh`로 토큰을 재발급받고 원래 요청을 재시도하면 됩니다. 그것도 401이면 다시 로그인해야 합니다.

---

## 7. Swagger에서 테스트하기

`http://localhost:8080/swagger-ui.html`

1. 로그인 API(`POST /api/v1/auth/login/{provider}`)를 먼저 호출해서 응답 본문의 **`accessToken`** 값을 복사합니다.
2. 화면 우측 상단의 **`Authorize` 🔓 버튼**을 누릅니다.
3. 입력창에 **토큰 값만** 붙여넣습니다.

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sZSI6...
```

> ⚠️ `Bearer `를 직접 쓰지 마세요. Swagger가 알아서 붙여줍니다. (`bearerFormat: JWT`로 설정되어 있습니다)

4. `Authorize` → `Close`. 이제 모든 요청에 토큰이 자동으로 실립니다.

토큰은 30분 뒤 만료되므로 401이 뜨면 1~4를 다시 하면 됩니다.

### Postman / curl로 할 때

```bash
curl -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"category":"FREE","title":"제목","content":"내용"}'
```

여기서는 **`Bearer ` 접두사를 직접 써야 합니다.** (`Bearer` 뒤에 공백 한 칸)

---

## 8. 테스트 코드 작성하기

컨트롤러 테스트에서는 **보안 필터를 끄고, 인증을 직접 세팅**합니다. 진짜 JWT를 만들 필요가 없습니다.

```java
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)   // 보안 필터 끄기
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUpAuthentication() {
        // @AuthenticationPrincipal 이 읽을 인증을 직접 넣어준다.
        // principal 타입은 JwtAuthenticationFilter 와 똑같이 AuthUser 여야 한다.
        // 여기서 타입을 틀리면 컨트롤러 파라미터가 null 이 되어 엉뚱한 NPE 로 실패한다.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthUser(USER_ID, Role.CUSTOMER),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();   // 다음 테스트에 새지 않도록 반드시 정리
    }

    @Test
    @DisplayName("createPost_인증된회원_201과생성된게시글")
    void createPost_인증된회원_201() throws Exception {
        // given
        given(postService.createPost(eq(USER_ID), any(), any(), any())).willReturn(...);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FREE\",\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isCreated());
    }
}
```

`@AfterEach`의 `clearContext()`를 빼먹으면 **다른 테스트가 이 인증 정보를 물려받아** 이상하게 통과하거나 실패합니다. 꼭 넣으세요.

실제 예시는 `src/test/java/.../domain/auth/controller/AuthControllerTest.java`를 보세요.

### ⚠️ `@PreAuthorize` 규칙은 위 테스트로 검증되지 않습니다

위 방식은 **보안을 꺼놓고**(`addFilters = false`) 컨트롤러의 입출력만 확인합니다. `@WebMvcTest` 슬라이스는 우리 `SecurityConfig`를 로드하지 않으므로, 거기 붙은 `@EnableMethodSecurity`도 동작하지 않습니다. 즉 **`@PreAuthorize`를 붙였든 안 붙였든 이 테스트는 똑같이 통과합니다.**

권한 규칙 자체를 검증하려면 `@SpringBootTest`로 실제 설정을 띄우고, `@WithMockUser(roles = "CUSTOMER")` 같은 걸로 권한을 바꿔가며 403이 나는지 확인해야 합니다. SpEL 오타(`hasRole('CAMPOWNER')`)는 컴파일 검사를 받지 않으니, **권한 제한을 새로 걸었다면 이 테스트를 반드시 한 번은 작성하세요.**

---

## 9. 자주 하는 실수

### 🚨 회원 id를 클라이언트한테 받기 — 절대 금지

```java
// ❌ 절대 이렇게 하지 마세요
@PostMapping("/api/v1/posts")
public ... createPost(@RequestBody PostCreateRequest request) {
    postService.createPost(request.userId(), ...);   // 요청 본문의 userId
}
```

이러면 아무나 `userId`를 `1`로 바꿔 보내서 **다른 사람 행세를 할 수 있습니다.** 남의 이름으로 글을 쓰고, 남의 예약을 취소할 수 있습니다.

회원 id는 **서버가 토큰에서 직접 꺼낸 값**(`user.id()`)만 믿으세요. DTO에 `userId` 필드가 있다면 그것부터 지우세요.

같은 이유로 **`role`도 클라이언트에게 받지 않습니다.**

### `user`가 `null`로 들어옵니다 / `NullPointerException`이 납니다

가장 흔한 원인은 **파라미터 타입을 틀린 것**입니다.

```java
@AuthenticationPrincipal Long userId    // ❌ 항상 null
@AuthenticationPrincipal AuthUser user  // ✅
```

`@AuthenticationPrincipal`은 타입이 안 맞으면 **예외 대신 `null`을 넘깁니다.** 그래서 컴파일도 되고 실행도 되다가, 서비스 안쪽에서 NPE로 뒤늦게 터집니다.

타입이 맞는데도 `null`이라면:

1. 그 경로가 `SecurityConfig`에서 **`permitAll()`로 열려 있습니다.** ([4절](#4-내-api를-로그인-필수로-만들려면) 표 확인)
2. 요청에 `Authorization` 헤더를 안 보냈습니다. (Swagger `Authorize` 버튼을 눌렀나요?)
3. `@AuthenticationPrincipal` 애너테이션을 안 붙였습니다.

### `@PreAuthorize`를 붙였는데 안 막힙니다

`@EnableMethodSecurity`는 켜져 있으니 애너테이션 자체는 동작합니다. 대개 이 둘 중 하나입니다.

1. **같은 클래스 안에서 그 메서드를 직접 호출**했습니다. 프록시를 안 타서 무시됩니다(self-invocation).
2. `private` 메서드에 붙였습니다. 프록시가 가로챌 수 없습니다.

반대로 **전부 403이 난다면** SpEL 오타를 의심하세요. `hasRole('CAMPOWNER')`처럼 존재하지 않는 권한을 쓰면 아무도 통과하지 못합니다.

### 로그아웃했는데 토큰이 계속 먹힙니다

**Redis가 안 떠 있을 겁니다.** 로그아웃된 토큰 목록을 Redis에 저장하는데, Redis 조회에 실패하면 요청을 통과시킵니다(fail-open).

```bash
docker compose up -d
```

자세한 건 [docker-setting.md](docker-setting.md)를 보세요.

### `import`가 헷갈립니다

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;   // 애너테이션
import com.basecamp.backend.common.model.AuthUser;                          // 우리가 만든 record
```

애너테이션은 `org.springframework.security.core.annotation` 패키지입니다. IDE가 다른 걸 추천하면 무시하세요.

### 로그아웃/탈퇴 API는 왜 `HttpServletRequest`를 받나요?

```java
public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user, HttpServletRequest request) {
```

그 두 API는 **토큰 문자열 자체**가 필요해서입니다(폐기하려고). **여러분이 만드는 일반 API에는 필요 없습니다.** `AuthUser`만 받으세요.

---

## 10. 체크리스트

새 API를 만들 때 이것만 확인하세요.

- [ ] 로그인이 필요한 API인가? → **아무 설정도 하지 않는다** (자동으로 보호됨)
- [ ] 컨트롤러에 `@AuthenticationPrincipal AuthUser user` 추가했는가? (`Long`이 아니라 `AuthUser`)
- [ ] 요청 DTO에 `userId`나 `role` 필드가 없는가?
- [ ] 남의 리소스를 건드리지 못하게 **서비스에서 소유권을 확인**했는가?
- [ ] 비로그인 공개가 필요하면 `SecurityConfig` 수정 + **PR 리뷰 요청**했는가?
- [ ] 특정 권한 전용 API라면 `@PreAuthorize`를 붙였고, **그 규칙을 테스트로 확인**했는가? (SpEL 오타는 컴파일 검사가 안 됨)
- [ ] 컨트롤러 경로를 바꿨다면 `SecurityConfig`의 `requestMatchers`도 같이 확인했는가?
- [ ] 컨트롤러 테스트에 `addFilters = false` + `SecurityContextHolder` 세팅 + `clearContext()` 했는가?

---

## 참고

- `security/SecurityConfig.java` — 어떤 경로가 열려 있고 막혀 있는지
- `common/model/AuthUser.java` — principal 타입. 왜 `UserDetails`가 아닌지 주석에 정리
- `security/JwtAuthenticationFilter.java` — 토큰을 검증하고 `AuthUser`를 넣어주는 곳
- `domain/post/controller/PostController.java` — 가장 단순한 사용 예시
- `domain/auth/controller/AuthController.java` — 토큰 문자열이 필요한 특수 케이스
- [docker-setting.md](docker-setting.md) — Redis(로그아웃/제재 처리)를 띄우는 법
- [이슈 #18](https://github.com/team-basecamp/basecamp-back/issues/18) — 관리자 회원 제재
- [이슈 #39](https://github.com/team-basecamp/basecamp-back/issues/39) — Refresh Token 회전 및 토큰 블랙리스트
