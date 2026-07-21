# BaseCamp Backend API 명세

> 현재 `dev` 브랜치에 구현된 전체 REST API 명세. 담당자별로 구분.
> 각 블록은 탭 구분(TSV)이므로 그대로 복사해 Excel에 붙여넣으면 9개 컬럼으로 분리된다.

## 컬럼 정의

| 컬럼 | 설명 |
|------|------|
| HTTP 메서드 | `GET` / `POST` (프로젝트 정책상 이 둘만 사용) |
| 경로 | 클래스 `@RequestMapping` + 메서드 매핑을 결합한 전체 경로 |
| 대분류(주체) | 관리자 / 캠핑장주 / 일반사용자 / 비회원 / 시스템 |
| 중분류(도메인) | 패키지 도메인명 |
| 소분류(기능명) | 기능 한글명 |
| 인증 필요 | `X`(공개) / `O(로그인)` / `O(ROLE_XXX)` |
| Request | PathVariable / QueryParam / RequestBody, Bean Validation 제약 포함 |
| Response | 상태코드 + 응답 DTO 필드 (`ApiResponse` 봉투 안의 `data` 기준) |
| Error Cases | `ErrorCode명(HTTP상태)` |

## 읽기 전 알아둘 것

- **응답 봉투**: 모든 정상 응답은 `ApiResponseWrapAdvice`가 `{success, message, data}`로 감싼다. 아래 Response 컬럼은 **봉투 안 `data`의 내용**이다. 유일한 예외는 `GET /api/v1/notifications/subscribe`(SseEmitter 반환이라 봉투 미적용).
- **권한명**: 캠핑장주 권한은 `@PreAuthorize("hasRole('CAMP_OWNER')")` 이므로 실제 권한명은 **`ROLE_CAMP_OWNER`** 다(`ROLE_OWNER` 아님).
- **인증 판정 근거**: `security/SecurityConfig.java`. 기본이 `anyRequest().authenticated()`이고, 공개(permitAll)는 auth 로그인 계열, `/api/v1/payments/webhook`, `GET /api/v1/camps/**`, `GET /api/v1/weather/**`, Swagger, GET 이미지 경로뿐이다.
- **Bean Validation 실패**는 `GlobalExceptionHandler`가 `INVALID_INPUT_VALUE(400)`으로 변환한다.
- **비회원으로 표기한 GET**은 "누구나 접근 가능"이란 뜻이며 로그인 사용자도 당연히 사용한다.

---

## 김진아 — auth / admin / campowner / notification (23개)

```
HTTP 메서드	경로	대분류(주체)	중분류(도메인)	소분류(기능명)	인증 필요	Request	Response	Error Cases
GET	/api/v1/auth/login/naver/state	비회원	auth	네이버 로그인 state 발급	X	없음	200: {state:String}	없음
POST	/api/v1/auth/login/{provider}	비회원	auth	소셜 로그인(코드 릴레이)	X	path: provider(String, kakao/google/naver), body: {code:String @NotBlank, state:String @Size(max=255), 네이버만 필수}	200: {accessToken, tokenType("Bearer"), userId:Long, email, nickname, role, profileImageUrl(nullable)} + Set-Cookie(refreshToken, HttpOnly)	UNSUPPORTED_PROVIDER(400), INVALID_INPUT_VALUE(400), INVALID_OAUTH_STATE(400), EMAIL_CONSENT_REQUIRED(400), INVALID_EMAIL(400), SOCIAL_TOKEN_FETCH_FAILED(502), SOCIAL_USERINFO_FETCH_FAILED(502), BLACKLISTED_USER(403), EMAIL_ALREADY_REGISTERED(409)
POST	/api/v1/auth/token/refresh	비회원	auth	액세스 토큰 재발급	X (쿠키의 refresh 토큰으로 검증)	cookie: refreshToken (body 없음)	200: {accessToken, tokenType("Bearer")} + Set-Cookie(회전된 refreshToken)	REFRESH_TOKEN_NOT_FOUND(401), EXPIRED_TOKEN(401), INVALID_REFRESH_TOKEN(401), BLACKLISTED_USER(403)
POST	/api/v1/auth/logout	일반사용자	auth	로그아웃	O(로그인)	header: Authorization Bearer, cookie: refreshToken	204: 본문 없음 + Set-Cookie(refreshToken 삭제)	없음(멱등 처리)
POST	/api/v1/auth/withdraw	일반사용자	auth	회원 탈퇴	O(로그인)	body: {reason:String @Size(max=500), 선택}	204: 본문 없음 + Set-Cookie(refreshToken 삭제)	USER_NOT_FOUND(404), INVALID_INPUT_VALUE(400)
POST	/api/v1/camp-owner/applications	일반사용자	campowner	캠핑업체 전환 신청	O(ROLE_CUSTOMER)	body: {businessNumber:String @NotBlank @Pattern(\d{10}), businessName:String @NotBlank @Size(max=100), representativeName:String @NotBlank @Size(max=50)}	201: {applicationId:Long, userId:Long, businessNumber, businessName, representativeName, status, rejectReason(nullable), createdAt, processedAt(nullable)}	INVALID_INPUT_VALUE(400), USER_NOT_FOUND(404), BLACKLISTED_USER(403), ACCESS_DENIED(403), ALREADY_CAMP_OWNER(409), CAMP_OWNER_APPLICATION_ALREADY_PENDING(409)
GET	/api/v1/camp-owner/applications/me	일반사용자	campowner	내 신청 상태 조회	O(로그인)	없음(인증 principal의 userId 사용)	200: CampOwnerApplicationResponse{applicationId, userId, businessNumber, businessName, representativeName, status, rejectReason, createdAt, processedAt}	CAMP_OWNER_APPLICATION_NOT_FOUND(404)
GET	/api/v1/admin/camp-owner/applications	관리자	admin	업체 전환 신청 목록 조회	O(ROLE_ADMIN)	query: status(ApplicationStatus, 기본 PENDING), page(int, 기본0), size(int, 기본20), sort(기본 createdAt DESC)	200: Page<CampOwnerApplicationResponse>	INVALID_INPUT_VALUE(400, status enum 변환 실패)
POST	/api/v1/admin/camp-owner/applications/{applicationId}/approve	관리자	admin	업체 전환 신청 승인	O(ROLE_ADMIN)	path: applicationId(Long)	204: 본문 없음	CAMP_OWNER_APPLICATION_NOT_FOUND(404), USER_NOT_FOUND(404), BLACKLISTED_USER(403), CAMP_OWNER_APPLICATION_ALREADY_PROCESSED(409), ALREADY_CAMP_OWNER(409)
POST	/api/v1/admin/camp-owner/applications/{applicationId}/reject	관리자	admin	업체 전환 신청 반려	O(ROLE_ADMIN)	path: applicationId(Long), body: {reason:String @NotBlank @Size(max=200)}	204: 본문 없음	INVALID_INPUT_VALUE(400), CAMP_OWNER_APPLICATION_NOT_FOUND(404), CAMP_OWNER_APPLICATION_ALREADY_PROCESSED(409)
GET	/api/v1/admin/posts/reports	관리자	admin	신고된 게시글 목록 조회	O(ROLE_ADMIN)	query: status(ReportStatus, 생략 시 PENDING), page(int, 기본0), size(int, 기본20), sort(기본 createdAt DESC)	200: Page<ReportedPostResponse>{reportId, postId, postTitle, postStatus, category, reason, description, reportStatus, reporterId, reporterNickname, createdAt}	INVALID_INPUT_VALUE(400, status enum 변환 실패)
GET	/api/v1/admin/posts/{postId}	관리자	admin	게시글 상세 조회(관리자용)	O(ROLE_ADMIN)	path: postId(Long)	200: {postId, userId, nickname, category, title, content, viewCount, status, blindReason(nullable), createdAt, updatedAt(nullable)}	POST_NOT_FOUND(404)
POST	/api/v1/admin/posts/{postId}/blind	관리자	admin	게시글 블라인드 처리	O(ROLE_ADMIN)	path: postId(Long), body: {reason:String @NotBlank @Size(max=200)}	204: 본문 없음	INVALID_INPUT_VALUE(400), POST_NOT_FOUND(404, 없거나 DELETED), POST_ALREADY_BLINDED(409)
POST	/api/v1/admin/posts/reports/{reportId}/reject	관리자	admin	신고 반려	O(ROLE_ADMIN)	path: reportId(Long)	204: 본문 없음	REPORT_NOT_FOUND(404), REPORT_ALREADY_PROCESSED(409)
GET	/api/v1/admin/users	관리자	admin	회원 목록 조회	O(ROLE_ADMIN)	query: status(UserStatus, 선택), role(Role, 선택), keyword(String, 선택, 닉네임/이메일 부분일치), page(int, 기본0), size(int, 기본20), sort(기본 createdAt DESC)	200: Page<AdminUserResponse>{userId, email, nickname, profileImageUrl(nullable), provider, role, status, createdAt, blacklistReason(nullable), blacklistedAt(nullable), deletedAt(nullable)}	INVALID_INPUT_VALUE(400, status/role enum 변환 실패)
POST	/api/v1/admin/users/{userId}/blacklist	관리자	admin	회원 제재(강제 로그아웃)	O(ROLE_ADMIN)	path: userId(Long), body: {reason:String @NotBlank @Size(max=200)}	204: 본문 없음	INVALID_INPUT_VALUE(400), USER_NOT_FOUND(404), USER_ALREADY_BLACKLISTED(409)
GET	/api/v1/admin/users/blacklist	관리자	admin	제재된 회원 목록 조회	O(ROLE_ADMIN)	query: page(int, 기본0), size(int, 기본20), sort(기본 blacklistedAt DESC)	200: Page<BlacklistedUserResponse>{userId, email, nickname, provider, blacklistReason, blacklistedAt}	없음
POST	/api/v1/admin/users/{userId}/blacklist/release	관리자	admin	회원 제재 해제	O(ROLE_ADMIN)	path: userId(Long)	204: 본문 없음	USER_NOT_FOUND(404), USER_NOT_BLACKLISTED(409)
GET	/api/v1/notifications/subscribe	일반사용자	notification	알림 구독(SSE)	O(로그인)	없음 (Accept: text/event-stream)	200: SseEmitter 스트림 — connect 이벤트("connected"), notification 이벤트(NotificationResponse). ApiResponse 봉투 미적용	없음
GET	/api/v1/notifications	일반사용자	notification	내 알림 목록 조회	O(로그인)	query: isRead(Boolean, 선택), page(int, 기본0), size(int, 기본10), sort(기본 createdAt DESC)	200: Page<NotificationResponse>{id, type, message, targetType, targetId, isRead, createdAt}	없음
GET	/api/v1/notifications/unread-count	일반사용자	notification	안읽은 알림 개수 조회	O(로그인)	없음	200: {count:long}	없음
POST	/api/v1/notifications/{notificationId}/read	일반사용자	notification	개별 알림 읽음 처리	O(로그인)	path: notificationId(Long)	200: 본문 없음	NOTIFICATION_NOT_FOUND(404), ACCESS_DENIED(403, 본인 알림 아님)
POST	/api/v1/notifications/read-all	일반사용자	notification	전체 알림 읽음 처리	O(로그인)	없음	200: 본문 없음	없음
```

---

## perish95 — reservation / review / payment (16개)

```
HTTP 메서드	경로	대분류(주체)	중분류(도메인)	소분류(기능명)	인증 필요	Request	Response	Error Cases
POST	/api/v1/reservations	일반사용자	reservation	예약 생성	O(로그인)	body: {campId:Long @NotNull @Positive, customerName @NotBlank, customerPhone @NotBlank @Pattern(010-XXXX-XXXX), checkInDate:LocalDate @NotNull @Future, checkOutDate:LocalDate @NotNull @Future, guestCount:int @Positive, totalPrice:Long @NotNull @Positive, specialRequest @Size(max=500)} + @AssertTrue(체크아웃>체크인)	201: ReservationResponse{id, campId, checkInDate, checkOutDate, guestCount, customerName, customerPhone, specialRequest, status, cancelDate, totalPrice, createdAt}	DUPLICATE_RESERVATION(409), INVALID_RESERVATION_PERIOD(400), USER_NOT_FOUND(404), CAMP_NOT_FOUND(404), INVALID_INPUT_VALUE(400)
POST	/api/v1/reservations/{reservationId}/cancel	일반사용자	reservation	예약 취소	O(로그인)	path: reservationId(Long)	200: ReservationResponse	RESERVATION_NOT_FOUND(404), ACCESS_DENIED(403), ALREADY_CANCELED_OR_REJECTED(400), PAYMENT_NOT_FOUND(404), PAYMENT_NOT_REFUNDABLE(404), PG_REFUND_FAILED(502), PG_NOT_CONFIGURED(503)
POST	/api/v1/reservations/{reservationId}/approve	캠핑장주	reservation	예약 수락	O(ROLE_CAMP_OWNER)	path: reservationId(Long)	200: ReservationResponse	RESERVATION_NOT_FOUND(404), ACCESS_DENIED(403), RESERVATION_NOT_PENDING(400), RESERVATION_EXPIRED(400)
POST	/api/v1/reservations/{reservationId}/reject	캠핑장주	reservation	예약 거절	O(ROLE_CAMP_OWNER)	path: reservationId(Long), body: {reason:String @NotBlank @Size(max=200)}	200: ReservationResponse	RESERVATION_NOT_FOUND(404), ACCESS_DENIED(403), RESERVATION_NOT_PENDING(400), PAYMENT_NOT_FOUND(404), PAYMENT_NOT_REFUNDABLE(404), PG_REFUND_FAILED(502), PG_NOT_CONFIGURED(503), INVALID_INPUT_VALUE(400)
GET	/api/v1/reservations/me	일반사용자	reservation	내 예약 목록 조회	O(로그인)	query: Pageable(page, size 기본10, sort 기본 createdAt DESC)	200: Page<ReservationListResponse>{id, campId, campName, campImage, checkInDate, checkOutDate, guestCount, totalPrice, customerName, customerPhone, specialRequest, status, rejectReason, cancelDate, createdAt}	없음
GET	/api/v1/reservations/camps/{campId}	캠핑장주	reservation	캠핑장별 예약 목록 조회	O(ROLE_CAMP_OWNER)	path: campId(Long), query: Pageable(page, size 기본10, sort 기본 createdAt DESC)	200: Page<ReservationResponse>	CAMP_NOT_FOUND(404), ACCESS_DENIED(403)
GET	/api/v1/reservations/stats	캠핑장주	reservation	예약 통계 조회	O(ROLE_CAMP_OWNER)	없음	200: {monthlyRevenue:long, monthlyReservations:long, yearlyReservations:long, pendingCount:long, averageRating:Double(nullable)}	없음
GET	/api/v1/reservations/stats/monthly	캠핑장주	reservation	월별 매출 통계 조회	O(ROLE_CAMP_OWNER)	없음	200: List<MonthlyRevenueResponse>{month:int, revenue:long, count:long} (1~12월 전체)	없음
POST	/api/v1/payments/prepare	일반사용자	payment	결제 준비	O(로그인)	body: {reservationId:Long @NotNull @Positive, paymentMethod:PaymentMethod(CARD/KAKAO_PAY/TOSS_PAY) @NotNull}	200: {storeId, channelKey, paymentId, orderName, totalAmount:Long, currency, payMethod, easyPayProvider, customerName, customerPhone, customerEmail}	RESERVATION_NOT_FOUND(404), ACCESS_DENIED(403), RESERVATION_NOT_PENDING_PAYMENT(400), PG_NOT_CONFIGURED(503), ALREADY_PAID(409), INVALID_INPUT_VALUE(400)
POST	/api/v1/payments/complete	일반사용자	payment	결제 완료 확인	O(로그인)	body: {paymentId:String @NotBlank @Size(max=80)}	201: PaymentResponse{id, reservationId, paymentId, amount:Long, paymentMethod, status(READY/PAID/REFUNDED/FAILED), failureReason, paidAt, createdAt}	PAYMENT_NOT_FOUND(404), ACCESS_DENIED(403), PG_PAYMENT_NOT_FOUND(404), PG_COMMUNICATION_FAILED(502), UNSUPPORTED_PAYMENT_METHOD(400), PG_NOT_CONFIGURED(503), PG_REFUND_FAILED(502), INVALID_INPUT_VALUE(400)
POST	/api/v1/payments/webhook	시스템(포트원 PG)	payment	포트원 결제 웹훅 수신	X (permitAll, 대신 HMAC-SHA256 서명 검증)	header: webhook-id, webhook-timestamp, webhook-signature + body: raw JSON({type, data.paymentId})	200: 본문 없음	WEBHOOK_SIGNATURE_INVALID(401), PG_NOT_CONFIGURED(503), PG_PAYMENT_NOT_FOUND(404), PG_COMMUNICATION_FAILED(502), UNSUPPORTED_PAYMENT_METHOD(400), PG_REFUND_FAILED(502)
POST	/api/v1/reservations/{reservationId}/reviews	일반사용자	review	리뷰 작성	O(로그인)	path: reservationId(Long), multipart/form-data: 파트 "request"(application/json, {rating:Integer @NotNull @DecimalMin(1) @DecimalMax(5), content:String @NotBlank @Size(max=1000)}), 파트 "images"(List<MultipartFile>, 선택)	201: ReviewResponse{reviewId, campId, reservationId, userId, nickname, rating:BigDecimal, content, imageUrls:List<String>, createdAt, updatedAt}	RESERVATION_NOT_FOUND(404), ACCESS_DENIED(403), REVIEW_NOT_ALLOWED_BEFORE_CHECKOUT(400), REVIEW_ALREADY_EXISTS(409), INVALID_IMAGE_TYPE(400), IMAGE_COUNT_EXCEEDED(400), IMAGE_UPLOAD_FAILED(500), INVALID_INPUT_VALUE(400)
POST	/api/v1/reviews/{reviewId}	일반사용자	review	리뷰 수정	O(로그인)	path: reviewId(Long), multipart/form-data: 파트 "request"(application/json, {rating:Integer @NotNull @DecimalMin(1) @DecimalMax(5), content:String @NotBlank @Size(max=1000), keepImageUrls:List<String> @Size(max=50) 각 원소 @NotBlank @Pattern(상대경로)}), 파트 "images"(List<MultipartFile>, 선택)	200: ReviewResponse	REVIEW_NOT_FOUND(404), ACCESS_DENIED(403), INVALID_INPUT_VALUE(400), IMAGE_COUNT_EXCEEDED(400), INVALID_IMAGE_TYPE(400), IMAGE_UPLOAD_FAILED(500)
POST	/api/v1/reviews/{reviewId}/delete	일반사용자	review	리뷰 삭제	O(로그인)	path: reviewId(Long)	204: 본문 없음	REVIEW_NOT_FOUND(404), ACCESS_DENIED(403)
GET	/api/v1/camps/{campId}/reviews	비회원	review	캠핑장 리뷰 목록 조회	X	path: campId(Long)	200: List<ReviewResponse>	없음
GET	/api/v1/reviews/me	일반사용자	review	내 리뷰 목록 조회	O(로그인)	없음	200: List<ReviewResponse>	없음
```

---

## Park beom hoon — post / comment / user (13개)

```
HTTP 메서드	경로	대분류(주체)	중분류(도메인)	소분류(기능명)	인증 필요	Request	Response	Error Cases
POST	/api/v1/posts	일반사용자	post	게시글 작성	O(로그인)	multipart/form-data: 파트 "request"(application/json, {category:String @NotBlank @Size(max=30) @Pattern(GENERAL|CAMP_MATE|RESERVATION_TRANSFER), title:String @NotBlank @Size(max=200), content:String @NotBlank}), 파트 "images"(List<MultipartFile>, 선택)	201: PostDetailResponse{postId, userId, nickname, category, title, content, viewCount, status, imageUrls:List<String>, createdAt, updatedAt}	USER_NOT_FOUND(404), INVALID_INPUT_VALUE(400), INVALID_IMAGE_TYPE(400), IMAGE_COUNT_EXCEEDED(400), IMAGE_UPLOAD_FAILED(500), IMAGE_SIZE_EXCEEDED(413)
GET	/api/v1/posts	일반사용자	post	게시글 목록 조회(커서 페이징)	O(로그인)	query: category(String, 선택, 생략/ALL이면 전체), cursor(String, 선택), size(int, 기본10, 1~50)	200: PostListCursorResponse{content:List<PostListResponse>{postId, category, title, nickname, createdAt, viewCount, commentCount}, hasNext:boolean, nextCursor:String(nullable)}	INVALID_INPUT_VALUE(400)
GET	/api/v1/posts/{postId}	일반사용자	post	게시글 상세 조회(조회수 증가, 작성자 본인은 미증가)	O(로그인)	path: postId(Long)	200: PostDetailResponse	POST_NOT_FOUND(404), POST_BLINDED(403)
POST	/api/v1/posts/{postId}/update	일반사용자	post	게시글 수정(이미지 전체 교체)	O(로그인)	path: postId(Long), multipart/form-data: 파트 "request"(application/json, {category @NotBlank @Size(max=30) @Pattern(GENERAL|CAMP_MATE|RESERVATION_TRANSFER), title @NotBlank @Size(max=200), content @NotBlank, keepImageUrls:List<String> 선택 @Size(max=50) 각 원소 @NotBlank @Pattern("/"로 시작하는 상대경로)}), 파트 "images"(List<MultipartFile>, 선택)	200: PostDetailResponse	POST_NOT_FOUND(404), POST_BLINDED(403), ACCESS_DENIED(403), INVALID_INPUT_VALUE(400), IMAGE_COUNT_EXCEEDED(400), INVALID_IMAGE_TYPE(400), IMAGE_UPLOAD_FAILED(500), IMAGE_SIZE_EXCEEDED(413)
POST	/api/v1/posts/{postId}/delete	일반사용자	post	게시글 삭제(소프트 삭제, 이미지는 하드 삭제)	O(로그인)	path: postId(Long)	200: PostDeleteResponse{redirectUrl:String("/api/v1/posts")}	POST_NOT_FOUND(404), ACCESS_DENIED(403)
POST	/api/v1/posts/{postId}/report	일반사용자	post	게시글 신고	O(로그인)	path: postId(Long), body: {reason:String @NotBlank @Pattern(SPAM|INAPPROPRIATE|ILLEGAL|ETC), description:String 선택 @Size(max=1000)}	201: PostReportResponse{reportId:Long, postId:Long, createdAt, message}	POST_NOT_FOUND(404), USER_NOT_FOUND(404), ALREADY_REPORTED_POST(409), INVALID_INPUT_VALUE(400)
POST	/api/v1/posts/{postId}/comments	일반사용자	comment	댓글 작성	O(로그인)	path: postId(Long), body: {content:String @NotBlank @Size(max=1000)}	201: CommentResponse{commentId, postId, userId, nickname, profileImageUrl(nullable), content, status, createdAt, updatedAt}	POST_NOT_FOUND(404), POST_BLINDED(403), ENTITY_NOT_FOUND(404), INVALID_INPUT_VALUE(400)
GET	/api/v1/posts/{postId}/comments	일반사용자	comment	댓글 목록 조회	O(로그인)	path: postId(Long)	200: List<CommentResponse> (없으면 빈 배열)	POST_NOT_FOUND(404), POST_BLINDED(403)
POST	/api/v1/comments/{commentId}/update	일반사용자	comment	댓글 수정	O(로그인)	path: commentId(Long), body: {content:String @NotBlank @Size(max=1000)}	200: CommentResponse	COMMENT_NOT_FOUND(404), POST_NOT_FOUND(404), POST_BLINDED(403), ACCESS_DENIED(403), INVALID_INPUT_VALUE(400)
POST	/api/v1/comments/{commentId}/delete	일반사용자, 관리자	comment	댓글 삭제(본인 DELETED / 관리자 BLINDED)	O(로그인) — ADMIN이면 타인 댓글도 가능	path: commentId(Long)	204: 본문 없음	COMMENT_NOT_FOUND(404), ACCESS_DENIED(403)
GET	/api/v1/users/me	일반사용자	user	내 프로필 조회	O(로그인)	없음	200: MyProfileResponse{userId, email, nickname, profileImageUrl(nullable), provider, createdAt}	USER_NOT_FOUND(404)
POST	/api/v1/users/me	일반사용자	user	내 프로필 수정	O(로그인)	body: {nickname:String @NotBlank @Size(max=50), profileImageUrl:String 선택 @Size(max=2048), 비우면 이미지 제거}	200: MyProfileResponse	USER_NOT_FOUND(404), INVALID_INPUT_VALUE(400)
GET	/api/v1/users/me/posts	일반사용자	user	내가 쓴 게시글 목록 조회(커서 페이징)	O(로그인)	query: cursor(String, 선택), size(int, 기본10, 1~50)	200: MyPostCursorResponse{content:List<MyPostResponse>{postId, title, createdAt, viewCount, commentCount}, hasNext:boolean, nextCursor(nullable)}	INVALID_INPUT_VALUE(400)
```

---

## JONGBEEN_LEE — camp / wishlist / weather (17개)

```
HTTP 메서드	경로	대분류(주체)	중분류(도메인)	소분류(기능명)	인증 필요	Request	Response	Error Cases
GET	/api/v1/camps	비회원	camp	전체 캠핑장 목록 조회	X	없음	200: List<CampResponseDto>	없음
GET	/api/v1/camps/{campId}	비회원	camp	캠핑장 상세 조회(campId)	X	path: campId(Long)	200: CampDetailResponseDto{resultCode, resultMsg, data:CampResponseDto(campId, contentId, facltNm, addr1, addr2, mapX, mapY, tel, induty, gnrlSiteCo, autoSiteCo, glampSiteCo, firstImageUrl, manageSttus, price, averageRating, reservationCount, createdAt, lineIntro, homepage, doNm, facilities, toiletCo, swrmCo, wtrplCo, extshrCo, glampInnerFclty, caravInnerFclty, operDeCl)}	CAMP_NOT_FOUND(404)
GET	/api/v1/camps/content/{contentId}	비회원	camp	캠핑장 상세 조회(고캠핑 contentId)	X	path: contentId(Long)	200: CampDetailResponseDto	CAMP_NOT_FOUND(404)
GET	/api/v1/camps/search	비회원	camp	캠핑장 검색(필터+정렬+페이징)	X	query: keyword(String, 선택), region(String, 선택), induty(String, 선택), priceMax(Integer, 선택), sort(String, 기본 "recommended"; rating/reviewCount/priceAsc/recent), pageNo(int, 기본1), numOfRows(int, 기본12)	200: CampListResponseDto{resultCode, resultMsg, data:List<CampResponseDto>, totalCount}	INVALID_PAGE_SIZE(400, numOfRows<=0)
GET	/api/v1/camps/hot	비회원	camp	HOT 캠핑장 조회	X	query: sortBy(String, 기본 "rating"; reservationCount 가능), pageNo(int, 기본1), numOfRows(int, 기본10)	200: CampListResponseDto	INVALID_PAGE_SIZE(400, numOfRows<=0)
GET	/api/v1/camps/search/name	비회원	camp	캠핑장 이름 검색	X	query: name(String, 필수)	200: List<CampResponseDto>	없음
GET	/api/v1/camps/search/address	비회원	camp	캠핑장 지역(주소) 검색	X	query: address(String, 필수)	200: List<CampResponseDto>	없음
GET	/api/v1/camps/my	캠핑장주	camp	내 캠핑장 목록 조회	O(ROLE_CAMP_OWNER)	없음(AuthUser 주입)	200: CampListResponseDto	UNAUTHORIZED(401, ownerId null), CAMP_NOT_ACCESSED(403, ownerId<=0), ACCESS_DENIED(403)
POST	/api/v1/camps/register	캠핑장주	camp	캠핑장 등록	O(ROLE_CAMP_OWNER)	multipart/form-data: 파트 "request"(application/json, {facltNm @NotBlank @Size(2~100), addr1 @NotBlank @Size(max=200), tel @NotBlank @Pattern(전화번호), induty @NotBlank @Size(max=100), price:Integer @NotNull @Min(0), addr2 @Size(max=200), gnrlSiteCo/autoSiteCo/glampSiteCo:Integer @Min(0) @Max(10000), lineIntro @Size(max=500), firstImageUrl @Size(max=255), homepage @URL(http/https) @Size(max=255)}), 파트 "images"(List<MultipartFile>, 선택)	201: CampResponseDto(withImages, imageUrls 포함)	INVALID_INPUT_VALUE(400), ACCESS_DENIED(403, ownerId null 또는 <=0), UNAUTHORIZED(401), INVALID_IMAGE_TYPE(400), IMAGE_COUNT_EXCEEDED(400), IMAGE_UPLOAD_FAILED(500), IMAGE_SIZE_EXCEEDED(413)
POST	/api/v1/camps/{campId}/update	캠핑장주	camp	캠핑장 정보 수정(이미지 부분 교체)	O(ROLE_CAMP_OWNER)	path: campId(Long), multipart/form-data: 파트 "request"(application/json, CampUpdateRequest{전 필드 선택, 제약은 등록과 동일, keepImageUrls:List<String> 선택 — null이면 기존 이미지 유지, 빈 배열이면 전부 삭제}), 파트 "images"(List<MultipartFile>, 선택)	200: CampResponseDto(withImages, imageUrls 포함)	CAMP_NOT_FOUND(404), CAMP_NOT_ACCESSED(403, 소유자 불일치), INVALID_INPUT_VALUE(400), ACCESS_DENIED(403), UNAUTHORIZED(401), INVALID_IMAGE_TYPE(400), IMAGE_COUNT_EXCEEDED(400), IMAGE_UPLOAD_FAILED(500), IMAGE_SIZE_EXCEEDED(413)
POST	/api/v1/camps/{campId}/delete	캠핑장주	camp	캠핑장 삭제(소프트 삭제, 첨부 이미지는 저장소에서 하드 삭제)	O(ROLE_CAMP_OWNER)	path: campId(Long)	204: 본문 없음	CAMP_NOT_FOUND(404), CAMP_NOT_ACCESSED(403, 소유자 불일치), ACCESS_DENIED(403), UNAUTHORIZED(401)
POST	/api/v1/camps/sync	관리자	camp	고캠핑 API 데이터 동기화(수동 전달)	O(ROLE_ADMIN)	body: List<GocampingApiResponseDto>{contentId:Long @NotNull, facltNm:String @NotBlank, addr1, mapX:Double, mapY:Double, tel, induty, gnrlSiteCo, autoSiteCo, glampSiteCo, firstImageUrl, manageSttus, intro, homepage, sbrsCl, hvofBgnde, hvofEndde}	200: String("캠핑장 데이터가 성공적으로 동기화되었습니다")	INVALID_INPUT_VALUE(400), UNAUTHORIZED(401), ACCESS_DENIED(403)
POST	/api/v1/camps/fetch	관리자	camp	고캠핑 API 전체 동기화(외부 직접 호출)	O(ROLE_ADMIN)	없음	200: String("고캠핑 API 전체 데이터가 성공적으로 동기화되었습니다")	GOCAMPING_SERVER_ERROR(500), UNAUTHORIZED(401), ACCESS_DENIED(403)
POST	/api/v1/camps/{campId}/wishlist	일반사용자	camp	찜 토글(등록/해제)	O(로그인)	path: campId(Long)	201(등록)/200(해제): {campId, wished:boolean}	CAMP_NOT_FOUND(404), WISHLIST_ALREADY_EXISTS(409, 동시 중복 요청), UNAUTHORIZED(401)
GET	/api/v1/wishlists/me	일반사용자	camp	내 찜 목록 조회	O(로그인)	없음(AuthUser 주입)	200: List<WishlistResponse>{campId, facltNm, addr1, firstImageUrl, createdAt}	UNAUTHORIZED(401)
GET	/api/v1/camps/{campId}/weather	비회원	weather	캠핑장 예약일 날씨 조회	X	path: campId(Long), query: checkInDate(LocalDate ISO, 필수), checkOutDate(LocalDate ISO, 필수)	200: {campId, weather:List<WeatherDay>{date, temp:Double, condition, humidity:Integer, icon}}	INVALID_INPUT_VALUE(400, checkIn>checkOut 또는 null), CAMP_NOT_FOUND(404)
GET	/api/v1/weather/regions	비회원	weather	시/도별 현재 날씨 조회	X	없음	200: List<RegionWeatherResponseDto>{regionName, temp:Double, condition, humidity:Integer, icon}	없음
```

---
