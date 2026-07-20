package com.basecamp.backend.common.model;

import com.basecamp.backend.common.enums.Role;

/**
 * 인증된 회원. {@code security.JwtAuthenticationFilter} 가 access 토큰 클레임으로 만들어 SecurityContext 의
 * principal 로 넣는다. 컨트롤러는 {@code @AuthenticationPrincipal AuthUser user} 로 받는다.
 *
 * <p><b>{@code security} 가 아니라 {@code common} 에 두는 이유:</b> 로그인이 필요한 모든 컨트롤러가 이 타입을 참조한다. {@code
 * security} 에 두면 {@code domain} 전체가 {@code security} 를 의존하게 되므로, 로직 없는 값 타입인 이것만 공용 sink 로 내려둔다. 그
 * 결과 {@code domain} 의 컨트롤러는 {@code security} 를 import 하지 않는다.
 *
 * <p>DB 를 조회하지 않고 토큰 클레임(userId, role)만으로 구성한다. 닉네임·이메일처럼 <b>바뀔 수 있는 값은 담지 않는다</b>. 토큰은 발급 후 만료(기본
 * 30분)까지 갱신되지 않으므로, 담아두면 옛 값이 그대로 따라다닌다. 그런 값은 서비스에서 조회한다.
 *
 * <p><b>{@code UserDetails} 를 구현하지 않는다.</b> 무상태 JWT 라 {@code DaoAuthenticationProvider}(아이디/비밀번호로
 * DB 를 조회해 인증하는 구현체)를 쓰지 않으므로, {@code getPassword()} 같이 쓰이지도 않을 메서드를 채울 이유가 없다.
 *
 * <p>principal 을 {@code Long} 이 아니라 이 타입으로 두는 이유는 두 가지다.
 *
 * <ol>
 *   <li>role 을 컨트롤러·서비스에서 쓸 수 있다(권한 문자열 authorities 로는 값을 꺼내기 번거롭다).
 *   <li>담을 값이 늘어나도 컨트롤러 시그니처를 바꾸지 않는다.
 * </ol>
 *
 * <p><b>주의:</b> {@code @AuthenticationPrincipal} 은 principal 타입이 파라미터 타입과 맞지 않으면 예외 대신 {@code null}
 * 을 넘긴다. 공개(permitAll) 엔드포인트에서 {@code null} 이 들어오는 것도 같은 경로다(익명 사용자의 principal 은 {@code
 * "anonymousUser"} 문자열이다). 로그인 필수 엔드포인트라면 {@code null} 검사는 불필요하지만, 공개 엔드포인트에서 받는다면 반드시 {@code null}
 * 을 처리해야 한다.
 */
public record AuthUser(Long id, Role role) {}
