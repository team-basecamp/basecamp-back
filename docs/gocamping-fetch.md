# 고캠핑 API 연동 — 동작 원리 & 전체 흐름

> BaseCamp 백엔드가 한국관광공사 **고캠핑(GoCamping) 공공데이터 API** 에서 캠핑장 데이터를 가져와 `camps` 테이블에 저장하는 전 과정 정리.
> 작성 기준 브랜치: `feat/camp-zzim`

---

## 1. 한눈에 보기

```
[앱 기동]
   │
   ├─ ApplicationReadyEvent 발생
   │        │
   │        ▼
   │   CampDataInitializer.initGocampingData()   ← @EventListener + @Async
   │        │  ① camp.data.init.enabled 켜져 있나?  (꺼져 있으면 빈 자체가 없음)
   │        │  ② campRepository.count() == 0 인가?  (데이터 있으면 skip)
   │        ▼
   └─→ CampService.fetchAndSaveCampsFromGocampingApi()
            │  while(hasMoreData) 페이지 루프 (numOfRows=100)
            │        │
            │        ├─ RestTemplate.getForEntity(url, GocampingApiResponse.class)
            │        │        └─ Jackson 역직렬화: response → body → items → item[]
            │        │                                     (GocampingApiResponseDto 리스트)
            │        ▼
            └─→ CampService.saveCampsFromApi(camps)
                     │  ③ 이미 있는 contentId 제외 (중복 방지)
                     │  ④ firstImageUrl 없는 캠핑장 제외
                     │  ⑤ Camp.fromGocampingApi(dto, generateRandomPrice())
                     ▼
                  campRepository.saveAll(newCamps)  →  MySQL `camps`
```

수동 트리거도 같은 서비스 메서드를 탄다:

```
POST /api/v1/camps/fetch  (ROLE_ADMIN)  →  fetchAndSaveCampsFromGocampingApi()
POST /api/v1/camps/sync   (ROLE_ADMIN)  →  saveCampsFromApi(요청 바디의 리스트)
```

---

## 2. 관련 파일 전체 목록

| 역할 | 파일 |
|------|------|
| 기동 시 자동 실행 트리거 | `domain/camp/config/CampDataInitializer.java` |
| 핵심 비즈니스 로직 | `domain/camp/service/CampService.java` |
| 외부 응답 DTO | `domain/camp/dto/request/GocampingApiResponseDto.java` |
| DTO → 엔티티 변환 | `domain/camp/entity/Camp.java` (`fromGocampingApi`, `truncate`) |
| 운영상태 문자열 매핑 | `domain/camp/entity/CampManageStatus.java` |
| 중복 판별 쿼리 | `domain/camp/repository/CampRepository.java` (`findAllContentIds`) |
| 관리자 수동 엔드포인트 | `domain/camp/controller/CampController.java` |
| HTTP 클라이언트 빈 | `common/config/RestTemplateConfig.java` |
| @Async 활성화 | `common/config/AsyncConfig.java` |
| 에러 코드 | `common/exception/ErrorCode.java` (`GOCAMPING_SERVER_ERROR`) |
| 설정 | `resources/application.yml` (`gocamping.*`, `camp.*`) |
| 스키마 | `resources/db/migration/V1__init_schema.sql` (`camps` 테이블) |

---

## 3. 단계별 상세

### 3-1. 자동 실행 트리거 — `CampDataInitializer`

`domain/camp/config/CampDataInitializer.java`

```java
@Configuration
@ConditionalOnProperty(
        name = "camp.data.init.enabled",
        havingValue = "true",
        matchIfMissing = true  // 설정 없으면 true로 기본값
)
public class CampDataInitializer {

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void initGocampingData() {
        try {
            if (campRepository.count() > 0) {   // 이미 데이터가 있으면 건너뛰기
                logger.info("📦 camps 테이블에 이미 {}개의 데이터가 있어 초기 동기화를 건너뜁니다.", campRepository.count());
                return;
            }
            logger.info("고캠핑 API에서 캠프 데이터 동기화 시작...");
            campService.fetchAndSaveCampsFromGocampingApi();
            logger.info(" 고캠핑 API 데이터 동기화 완료!");
        } catch (Exception e) {
            // 예외를 로깅하지만 앱 구동을 막지 않음
            logger.error(" 고캠핑 데이터 동기화 실패 (앱은 정상 구동됩니다): {}", e.getMessage(), e);
        }
    }
}
```

**동작을 결정하는 3개 장치**

1. **`@ConditionalOnProperty(matchIfMissing = true)`**
   → `camp.data.init.enabled` 값이 **아예 없으면 켜진 것으로 간주**한다. "설정한 적 없는데 왜 fetch 되지?" 의 직접적인 원인.
   `false` 로 주면 빈 자체가 컨텍스트에 등록되지 않아 리스너도 존재하지 않게 된다.

2. **`@EventListener(ApplicationReadyEvent.class)`**
   `ApplicationReadyEvent` 는 빈 초기화 + 내장 톰캣 기동까지 **모두 끝난 뒤** 마지막에 발행되는 이벤트다. 즉 DB 커넥션풀·Flyway·JPA가 다 준비된 상태에서 안전하게 실행된다.

3. **`@Async`**
   별도 스레드에서 실행되어 기동 스레드를 막지 않는다. `@Async` 는 프록시 기반이라 `@EnableAsync` 가 있어야 실제로 동작하는데, 이 프로젝트는 `common/config/AsyncConfig.java` 에 선언되어 있다.

   ```java
   // @Async가 실제로 별도 스레드에서 실행되려면 AsyncAnnotationBeanPostProcessor가
   // 등록돼 있어야 하는데, 이건 @EnableAsync를 붙여야 활성화된다.
   @Configuration
   @EnableAsync
   public class AsyncConfig {}
   ```

**멱등성 가드**: `campRepository.count() > 0` 이면 즉시 return. 그래서 한 번 데이터가 채워지면 재기동해도 다시 fetch 하지 않는다. 반대로 말하면 **DB를 비운 직후 첫 기동에서만** 대량 fetch가 발생한다.

**실패 격리**: 전체가 `try-catch(Exception)` 로 감싸져 있어 외부 API 장애가 앱 기동을 막지 않는다. 로그만 남고 서비스는 정상 구동된다.

---

### 3-2. 페이지 순회 & HTTP 호출 — `fetchAndSaveCampsFromGocampingApi()`

`domain/camp/service/CampService.java:121`

```java
@Transactional
public void fetchAndSaveCampsFromGocampingApi() {
    try {
        int pageNo = 1;
        int numOfRows = 100;
        boolean hasMoreData = true;
        int totalReceived = 0;
        int totalSaved = 0;

        while (hasMoreData) {
            String url = gocampingApiUrl + "?serviceKey=" + gocampingApiKey
                    + "&numOfRows=" + numOfRows
                    + "&pageNo=" + pageNo
                    + "&MobileOS=ETC&MobileApp=basecamp&_type=json";

            ResponseEntity<GocampingApiResponse> response =
                    restTemplate.getForEntity(url, GocampingApiResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ResponseBody responseBody = response.getBody().getResponse();
                Body body   = responseBody != null ? responseBody.getBody()  : null;
                Items items = body         != null ? body.getItems()         : null;
                List<GocampingApiResponseDto> camps = items != null ? items.getItem() : null;

                if (camps == null || camps.isEmpty()) {
                    hasMoreData = false;             // 마지막 페이지 넘어감 → 종료
                } else {
                    // receivedCount(응답 개수)와 savedCount(실제 저장 건수)는 다르다.
                    // 종료 판단은 반드시 receivedCount 로 — savedCount 로 하면 전부 중복인 페이지에서 조기 종료된다.
                    int receivedCount = camps.size();
                    int savedCount = saveCampsFromApi(camps);
                    totalReceived += receivedCount;
                    totalSaved += savedCount;
                    log.info("페이지 {}: {}건 수신, {}건 신규 저장", pageNo, receivedCount, savedCount);
                    if (receivedCount < numOfRows) {  // 한 페이지 못 채움 → 마지막 페이지
                        hasMoreData = false;
                    }
                    pageNo++;
                }
            } else {
                throw new BusinessException(ErrorCode.GOCAMPING_SERVER_ERROR, "고캠핑 API 응답이 비정상입니다");
            }
        }
    } catch (BusinessException e) {
        throw e;                                     // 비즈니스 예외는 그대로 전파
    } catch (RestClientException e) {
        log.error("고캠핑 API 호출 실패: {}", e.getClass().getSimpleName());
        throw new BusinessException(ErrorCode.GOCAMPING_SERVER_ERROR, "고캠핑 API 호출 중 오류가 발생했습니다");
    }
}
```

**요청 쿼리 파라미터**

| 파라미터 | 값 | 의미 |
|----------|-----|------|
| `serviceKey` | `${GOCAMPING_API_KEY}` | 공공데이터포털 인증키 |
| `numOfRows` | `100` | 한 페이지당 개수 |
| `pageNo` | `1..N` | 페이지 번호 (루프에서 증가) |
| `MobileOS` | `ETC` | 필수 파라미터 (고정) |
| `MobileApp` | `basecamp` | 앱 식별용 (고정) |
| `_type` | `json` | XML 대신 JSON 응답 요청 |

베이스 URL: `https://apis.data.go.kr/B551011/GoCamping/basedList`

**종료 조건은 두 가지**
- 응답 `item` 배열이 비었을 때 → 페이지를 다 돈 것
- 받은 개수 `< numOfRows` → 마지막 페이지

> ⚠️ **종료 판단에는 반드시 `receivedCount`(API 응답 개수)를 쓴다.**
> `savedCount`(실제 저장 건수)로 바꾸면, 이미 데이터가 찬 DB에서 재동기화할 때
> 1페이지 100건이 전부 중복 → 저장 0건 → `0 < 100` 성립 → **첫 페이지에서 루프가 조용히 멈춘다.**
> 두 값은 이름이 비슷해도 역할이 전혀 다르므로 절대 합치지 말 것.

**예외 흐름**
- 네트워크/타임아웃/4xx·5xx → `RestClientException` → `BusinessException(GOCAMPING_SERVER_ERROR)` 로 변환
- 2xx 인데 body 가 null → 곧바로 `BusinessException(GOCAMPING_SERVER_ERROR)`
- `ErrorCode.GOCAMPING_SERVER_ERROR` = `500 / CP004 / "고캠핑 서버 내부 오류가 발생했습니다."`
- 최종 응답 변환은 `GlobalExceptionHandler` 가 공통 봉투(`ApiResponse`)로 처리

---

### 3-3. HTTP 클라이언트 — `RestTemplateConfig`

`common/config/RestTemplateConfig.java`

```java
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(ClientHttpRequestFactories.get(settings));
    }
}
```

- 연결 5초 / 읽기 10초 타임아웃. 무한 대기를 막는다.
- 이 빈 하나를 `CampService`, `KakaoGeocodingClient`, `WeatherClient` 가 공유한다.

---

### 3-4. JSON 역직렬화 구조

고캠핑 API 실제 응답은 4겹으로 중첩되어 있다.

```json
{
  "response": {
    "header": { "resultCode": "0000", "resultMsg": "OK" },
    "body": {
      "items": { "item": [ { "contentId": 100, "facltNm": "...", ... } ] },
      "numOfRows": 100, "pageNo": 1, "totalCount": 3200
    }
  }
}
```

이 구조를 그대로 받기 위해 `CampService` 안에 **package-private static 중첩 클래스 4개**가 선언되어 있다 (`CampService.java:285~327`).

```java
@JsonIgnoreProperties(ignoreUnknown = true)
static class GocampingApiResponse { private ResponseBody response; /* getter/setter */ }

@JsonIgnoreProperties(ignoreUnknown = true)
static class ResponseBody { private Body body; }

@JsonIgnoreProperties(ignoreUnknown = true)
static class Body { private Items items; }

@JsonIgnoreProperties(ignoreUnknown = true)
static class Items { private List<GocampingApiResponseDto> item; }
```

`@JsonIgnoreProperties(ignoreUnknown = true)` 덕분에 매핑하지 않은 `header`, `totalCount` 등이 있어도 역직렬화가 깨지지 않는다. 서비스 코드에서는 **모든 단계를 null-safe 삼항 연산자로** 벗겨낸다 — 응답 구조가 일부 비어 와도 NPE 없이 "데이터 없음"으로 흘러가게 하기 위함이다.

---

### 3-5. 응답 DTO — `GocampingApiResponseDto`

`domain/camp/dto/request/GocampingApiResponseDto.java`

```java
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GocampingApiResponseDto {
    @NotNull  @JsonProperty("contentId")     private Long    contentId;      // 캠핑장 고유 ID
    @NotBlank @JsonProperty("facltNm")       private String  facltNm;        // 캠핑장 이름
              @JsonProperty("addr1")         private String  addr1;          // 기본 주소
              @JsonProperty("mapX")          private Double  mapX;           // 경도
              @JsonProperty("mapY")          private Double  mapY;           // 위도
              @JsonProperty("tel")           private String  tel;
              @JsonProperty("induty")        private String  induty;         // 시설 유형
              @JsonProperty("gnrlSiteCo")    private Integer gnrlSiteCo;     // 일반 야영장 수
              @JsonProperty("autoSiteCo")    private Integer autoSiteCo;     // 오토캠핑 수
              @JsonProperty("glampSiteCo")   private Integer glampSiteCo;    // 글램핑 수
              @JsonProperty("firstImageUrl") private String  firstImageUrl;  // 대표 이미지
              @JsonProperty("manageSttus")   private String  manageSttus;    // 운영/휴장/폐장
              @JsonProperty("intro")         private String  intro;          // 소개글
              @JsonProperty("homepage")      private String  homepage;
              @JsonProperty("sbrsCl")        private String  sbrsCl;         // 부대시설(콤마 구분)
              @JsonProperty("hvofBgnde")     private String  hvofBgnde;      // 운영 시작일 YYYYMMDD
              @JsonProperty("hvofEndde")     private String  hvofEndde;      // 운영 종료일 YYYYMMDD
}
```

- `@NotNull` / `@NotBlank` 는 **`POST /api/v1/camps/sync` 엔드포인트에서 `@Valid` 로 검증될 때만** 동작한다. 자동 fetch 경로(RestTemplate 역직렬화)에서는 Bean Validation 이 개입하지 않는다.
- setter 없이 `@Getter` + `@NoArgsConstructor` 만 있는데, Jackson 이 리플렉션으로 필드에 직접 주입하므로 문제없다.

---

### 3-6. 저장 로직 — `saveCampsFromApi()`

`domain/camp/service/CampService.java:83`

```java
// 반환값은 "실제로 새로 저장한 건수". 받은 개수(apiCamps.size())와 다르다 —
// 이미 있는 contentId 와 대표 이미지가 없는 항목은 걸러지기 때문.
@Transactional
public int saveCampsFromApi(List<GocampingApiResponseDto> apiCamps){
    if (apiCamps == null || apiCamps.isEmpty()){
        return 0;
    }
    // DB 에 이미 저장이 된 contentId를 모두 가져오기
    Set<Long> existingContentIds = campRepository.findAllContentIds()
            .stream()
            .collect(Collectors.toSet());

    // 새로운 데이터만 필터링 하고 Entity로 변환 하기
    List<Camp> newCamps = apiCamps.stream()
            .filter(dto -> !existingContentIds.contains(dto.getContentId()))     // ① 중복 제외
            .filter(dto -> StringUtils.hasText(dto.getFirstImageUrl()))          // ② 이미지 없으면 제외
            .map(dto -> Camp.fromGocampingApi(dto, generateRandomPrice()))       // ③ 엔티티 변환
            .collect(Collectors.toList());

    if(!newCamps.isEmpty()){
        campRepository.saveAll(newCamps);
    }
    return newCamps.size();          // 호출부의 로그/집계는 이 값을 쓴다
}
```

**필터 ① — 중복 제거**
`camps` 테이블에는 `UNIQUE KEY uq_camps_content_id (content_id)` 가 걸려 있다. 애플리케이션 레벨에서 미리 걸러 유니크 제약 위반을 피한다.

```java
// CampRepository
@Query("SELECT c.contentId FROM Camp c")
List<Long> findAllContentIds();
```

> 참고: 자체 등록 캠핑장은 `contentId` 가 `null` 이라 이 리스트에 `null` 이 섞인다. `HashSet` 은 `null` 을 담을 수 있고 `contains(null)` 도 안전하므로 동작에는 문제가 없다.

**필터 ② — 대표 이미지 필수**
`firstImageUrl` 이 비어 있는 캠핑장은 아예 저장하지 않는다. 목록 UI가 이미지 없는 카드로 깨지는 걸 막기 위한 정책적 선택.

**필터 ③ — 가격 생성**
고캠핑 API는 **가격 정보를 제공하지 않는다.** 그래서 설정 범위 안에서 임의 가격을 부여한다.

```java
private int generateRandomPrice() {
    int steps = (defaultPriceMax - defaultPriceMin) / defaultPriceUnit + 1;
    return defaultPriceMin + ThreadLocalRandom.current().nextInt(steps) * defaultPriceUnit;
}
```

기본값 기준 → `50,000 ~ 100,000` 원 사이에서 `1,000` 원 단위 랜덤.

설정이 잘못되면 `generateRandomPrice()` 가 런타임에 `ArithmeticException`(0으로 나눔) / `IllegalArgumentException`(음수 bound) 으로 터지므로, **기동 시점에 미리 검증**한다.

```java
@PostConstruct
private void validatePricePolicy() {
    if (defaultPriceMin < 0 || defaultPriceMax < defaultPriceMin || defaultPriceUnit <= 0) {
        throw new IllegalStateException(...);   // min >= 0, max >= min, unit > 0
    }
}
```

---

### 3-7. DTO → 엔티티 변환 — `Camp.fromGocampingApi()`

`domain/camp/entity/Camp.java:197`

```java
public static Camp fromGocampingApi(GocampingApiResponseDto dto, int price) {
    return Camp.builder()
            .contentId(dto.getContentId())
            .facltNm(dto.getFacltNm())
            .addr1(dto.getAddr1())
            .mapX(dto.getMapX() != null ? new BigDecimal(dto.getMapX().toString()) : null)
            .mapY(dto.getMapY() != null ? new BigDecimal(dto.getMapY().toString()) : null)
            .tel(dto.getTel())
            .induty(dto.getInduty())
            .gnrlSiteCo(dto.getGnrlSiteCo())
            .autoSiteCo(dto.getAutoSiteCo())
            .glampSiteCo(dto.getGlampSiteCo())
            .firstImageUrl(dto.getFirstImageUrl())
            .manageSttus(CampManageStatus.fromLabel(dto.getManageSttus()))
            .lineIntro(truncate(dto.getIntro(), 500))
            .homepage(truncate(dto.getHomepage(), 255))
            .sbrsCl(truncate(dto.getSbrsCl(), 500))
            .price(price)
            .averageRating(new BigDecimal("0.0"))
            .reservationCount(0)
            .createdAt(LocalDateTime.now())
            .build();
}
```

포인트 4가지:

1. **좌표 타입 변환** — API는 `Double`, 엔티티/DB는 `DECIMAL`. 부동소수 오차를 피하려고 `Double → String → BigDecimal` 로 우회한다.
2. **길이 잘라내기** — 원본 데이터가 컬럼 길이를 넘는 경우가 있어 저장 전에 자른다.
   ```java
   private static String truncate(String value, int maxLength) {
       if (value == null || value.length() <= maxLength) return value;
       return value.substring(0, maxLength);
   }
   ```
3. **운영상태 매핑** — 한글 라벨을 enum 으로 변환하되, **모르는 값이 와도 예외를 던지지 않는다.**
   ```java
   public static CampManageStatus fromLabel(String label) {
       return switch (label) {
           case "운영" -> OPERATING;
           case "휴장" -> SUSPENDED;
           case "폐장" -> CLOSED;
           case null, default -> {
               log.warn("알 수 없는 운영 상태 값 '{}' - 기본값(OPERATING)으로 처리합니다", label);
               yield OPERATING;
           }
       };
   }
   ```
   로우 하나의 이상값 때문에 목록/검색 API 전체가 죽는 걸 막기 위한 설계.
4. **`ownerId` 를 세팅하지 않는다** — 고캠핑 연동 캠핑장은 `content_id` 만, 직접 등록 캠핑장은 `owner_id` 만 갖는다. DB에 XOR 제약이 걸려 있다.

---

### 3-8. 저장 대상 테이블 — `camps`

`resources/db/migration/V1__init_schema.sql:67`

```sql
CREATE TABLE camps (
    camp_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '캠핑장 고유 식별자',
    content_id        BIGINT                               COMMENT '고캠핑 API contentId (외부 연동)',
    owner_id          BIGINT                               COMMENT '캠핑업체 소유자 (직접 등록 시)',
    faclt_nm          VARCHAR(100) NOT NULL                COMMENT '캠핑장 이름',
    ...
    first_image_url   TEXT                                 COMMENT '캠핑장 대표 사진 URL',
    manage_sttus      VARCHAR(20)  NOT NULL DEFAULT '운영'  COMMENT '운영 / 휴장 / 폐장',
    average_rating    DECIMAL(3,2) NOT NULL DEFAULT 0.00   COMMENT '리뷰 평균 평점 (캐싱 값)',
    reservation_count INT          NOT NULL DEFAULT 0      COMMENT '누적 예약 건수',

    PRIMARY KEY (camp_id),
    UNIQUE KEY uq_camps_content_id (content_id),
    ...
    CONSTRAINT chk_camps_source
        CHECK ( (content_id IS NOT NULL) <> (owner_id IS NOT NULL) )
);
```

- `uq_camps_content_id` → 고캠핑 캠핑장 중복 저장 방지 (DB 레벨 최후 방어선)
- `chk_camps_source` → **content_id XOR owner_id**. 고캠핑 연동본과 직접 등록본을 구조적으로 구분

---

### 3-9. 관리자 수동 엔드포인트 — `CampController`

`domain/camp/controller/CampController.java`

```java
// 관리자가 전달한 고캠핑 캠핑장 목록을 DB에 동기화 (바디에 배열을 직접 넣는 방식)
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/sync")
public ResponseEntity<String> syncCamps(@Valid @RequestBody List<@Valid GocampingApiResponseDto> apiCamps) {
    campService.saveCampsFromApi(apiCamps);
    return ResponseEntity.ok("캠핑장 데이터가 성공적으로 동기화되었습니다");
}

// 고캠핑 API 전체를 서버가 직접 호출해서 저장 (기동 시 자동 동기화와 같은 로직)
// 경로가 /api/v1/admin 아래가 아니라 SecurityConfig 의 URL 규칙으로는 묶이지 않는다.
// 메서드 하나에만 걸리는 규칙이므로 @PreAuthorize 로 보호한다.
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/fetch")
public ResponseEntity<String> fetchCamps() {
    campService.fetchAndSaveCampsFromGocampingApi();
    return ResponseEntity.ok("고캠핑 API 전체 데이터가 성공적으로 동기화되었습니다");
}
```

| 엔드포인트 | 권한 | 하는 일 |
|-----------|------|---------|
| `POST /api/v1/camps/fetch` | `ROLE_ADMIN` | 서버가 고캠핑 API를 전 페이지 호출해서 저장 |
| `POST /api/v1/camps/sync` | `ROLE_ADMIN` | 요청 바디로 받은 캠핑장 배열만 저장 |

두 경로 모두 `/api/v1/admin/**` 아래가 아니라 URL 패턴 기반 보안 규칙에 걸리지 않으므로 **메서드 시큐리티(`@PreAuthorize`)로 개별 보호**한다.

---

## 4. 설정 정리

`resources/application.yml`

```yaml
# gocamping API
gocamping:
  api:
    key: ${GOCAMPING_API_KEY:dummy-gocamping-api-key}
    url: https://apis.data.go.kr/B551011/GoCamping/basedList

camp:
  default-price:                                   # 고캠핑 API가 가격을 안 주므로 임의 부여
    min:  ${CAMP_DEFAULT_PRICE_MIN:50000}
    max:  ${CAMP_DEFAULT_PRICE_MAX:100000}
    unit: ${CAMP_DEFAULT_PRICE_UNIT:1000}
  data:
    init:
      enabled: ${CAMP_DATA_INIT_ENABLED:false}     # 기동 시 자동 동기화 on/off
```

| 프로퍼티 | 환경변수 | 기본값 | 설명 |
|----------|----------|--------|------|
| `gocamping.api.key` | `GOCAMPING_API_KEY` | dummy | 공공데이터포털 서비스키. 더미면 호출 실패 |
| `gocamping.api.url` | — | basedList | 고캠핑 목록 조회 엔드포인트 |
| `camp.default-price.min` | `CAMP_DEFAULT_PRICE_MIN` | 50000 | 임의 가격 하한 |
| `camp.default-price.max` | `CAMP_DEFAULT_PRICE_MAX` | 100000 | 임의 가격 상한 |
| `camp.default-price.unit` | `CAMP_DEFAULT_PRICE_UNIT` | 1000 | 가격 단위 |
| `camp.data.init.enabled` | `CAMP_DATA_INIT_ENABLED` | **false** | 기동 시 자동 동기화 여부 |

> ⚠️ **`camp.data.init.enabled` 는 `@ConditionalOnProperty(matchIfMissing = true)` 이므로, 이 키를 yml 에서 아예 지우면 다시 "켜짐" 으로 되돌아간다.** 끄고 싶으면 명시적으로 `false` 를 남겨둬야 한다.

---

## 5. 자주 묻는 것 / 트러블슈팅

**Q. 앱만 켰는데 왜 고캠핑 API 호출 로그가 뜨나요?**
`CampDataInitializer` 가 `ApplicationReadyEvent` 에 붙어 있고, `camp.data.init.enabled` 가 `matchIfMissing = true` 라 **설정을 안 하면 켜진 상태가 기본**이기 때문입니다. 게다가 `camps` 테이블이 비어 있을 때만 도므로, DB를 초기화한 직후 첫 기동에서 특히 눈에 띕니다.

**Q. 자동 동기화를 끄려면?**
```yaml
camp:
  data:
    init:
      enabled: false
```
또는 환경변수 `CAMP_DATA_INIT_ENABLED=false`. 끄면 `CampDataInitializer` 빈 자체가 등록되지 않습니다.

**Q. 끈 상태에서 데이터를 채우려면?**
관리자 계정으로 `POST /api/v1/camps/fetch` 호출.

**Q. "고캠핑 서버 내부 오류가 발생했습니다" (CP004) 가 뜹니다.**
- `GOCAMPING_API_KEY` 가 더미값이거나 만료 → 공공데이터포털에서 키 확인
- 공공데이터포털 점검/장애
- 타임아웃 (연결 5초 / 읽기 10초) — `RestTemplateConfig` 참고

**Q. API 응답에는 있는데 DB에 안 들어간 캠핑장이 있습니다.**
`saveCampsFromApi()` 의 두 필터 때문일 가능성이 높습니다.
1. 이미 같은 `contentId` 가 저장돼 있음
2. `firstImageUrl` 이 비어 있음 → **정책상 저장하지 않음**

**Q. 가격이 실제 캠핑장 가격과 다릅니다.**
고캠핑 API가 가격을 제공하지 않아 `generateRandomPrice()` 로 임의 부여한 값입니다. 실제 가격이 아닙니다.

---

## 6. 알려진 개선 여지

| 항목 | 내용 |
|------|------|
| 트랜잭션 범위 | `fetchAndSaveCampsFromGocampingApi()` 에 `@Transactional` 이 걸려 있어, 전 페이지 순회(수십 회 HTTP 호출) 동안 DB 커넥션을 잡고 있다. 페이지 단위 트랜잭션으로 쪼개는 편이 낫다. |
| N회 반복 쿼리 | `saveCampsFromApi()` 가 페이지마다 `findAllContentIds()` 로 **전체** contentId 를 다시 조회한다. 데이터가 커질수록 부담이 커진다. |
| 서비스키 로깅 위험 | `serviceKey` 가 URL 쿼리스트링에 그대로 들어간다. 예외 메시지나 액세스 로그에 URL 이 찍히면 키가 노출될 수 있다. |
| 갱신 없음 | 신규 `contentId` 만 저장하고 기존 로우는 **절대 갱신하지 않는다.** 고캠핑 쪽 정보가 바뀌어도 반영되지 않는다. |
| 재시도 없음 | 페이지 중간에 실패하면 그 지점에서 중단된다. 재시도/재개 로직이 없다. |

---

## 7. 관련 로그 메시지 모음

기동 시 이 로그들이 보이면 자동 동기화가 도는 중이다.

```
📦 camps 테이블에 이미 {N}개의 데이터가 있어 초기 동기화를 건너뜁니다.   ← skip (정상)
고캠핑 API에서 캠프 데이터 동기화 시작...
고캠핑 API 호출 중... (페이지: 1)
고캠핑 API 응답 - 페이지: 1, 받은 캠핑장 개수: 100                      ← DEBUG
첫 번째 캠핑장 - contentId: ..., facltNm: ..., addr1: ...              ← DEBUG
페이지 1: 100건 수신, 87건 신규 저장
마지막 페이지입니다  /  모든 페이지를 받았습니다
총 {수신}건 수신, {저장}개의 캠핑장이 새로 저장되었습니다 (중복/대표이미지 없음 {N}건 제외)
 고캠핑 API 데이터 동기화 완료!
고캠핑 데이터 동기화 실패 (앱은 정상 구동됩니다): {메시지}              ← 실패해도 앱은 뜬다
```

**수신 건수와 저장 건수가 다른 건 정상이다.** 차이는 두 필터에서 발생한다.
- 이미 저장된 `contentId` (재동기화 시 대부분 여기서 걸린다)
- `firstImageUrl` 이 비어 있는 캠핑장

두 값이 크게 벌어진다고 오류가 아니며, 재동기화에서는 오히려 `저장 = 0` 이 정상이다.

DEBUG 로그는 `logging.level.com.basecamp.backend: debug` 설정 덕분에 로컬에서 보인다.