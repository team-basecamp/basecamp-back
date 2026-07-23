# MinIO 이미지 저장소 로컬 세팅 가이드

> 이미지 업로드/저장이 어떻게 도는지 처음 보는 팀원을 위한 문서입니다. 순서대로 따라 하면 됩니다.
> Docker 자체가 처음이라면 [docker-setting.md](docker-setting.md)의 3절(Docker 설치)을 먼저 보세요.

## 목차

1. [왜 MinIO인가](#1-왜-minio인가)
2. [MinIO 없이도 되는 개발 / 반드시 필요한 개발](#2-minio-없이도-되는-개발--반드시-필요한-개발)
3. [띄우기](#3-띄우기)
4. [잘 떴는지 확인하기](#4-잘-떴는지-확인하기)
5. [BaseCamp가 MinIO를 어떻게 쓰는가](#5-basecamp가-minio를-어떻게-쓰는가)
6. [환경변수](#6-환경변수)
7. [자주 쓰는 명령어](#7-자주-쓰는-명령어)
8. [문제 해결](#8-문제-해결)

---

## 1. 왜 MinIO인가

전에는 업로드한 이미지를 **서버 로컬 디스크(`uploads/`)** 에 저장하고 `/images/**` 경로로 되돌려줬습니다. 이 방식은 서버가 파일 서버를 겸하게 만들고, 서버가 여러 대가 되면 파일이 한 대에만 있어 깨지며, 스토리지 관심사가 도메인 코드에 섞입니다.

**MinIO는 S3 호환 오브젝트 스토리지**입니다. AWS S3와 같은 API를 쓰므로, 로컬에서는 MinIO로 개발하고 운영에서는 S3로 바꾸기만 하면 됩니다. 애플리케이션은 파일을 직접 들고 있지 않고 **저장소에 올린 뒤 공개 URL만 DB에 담습니다.**

> **이 프로젝트는 로컬 전용**이라, 버킷을 **anonymous download**(URL을 아는 누구나 GET 가능)로 열어 뒀습니다. 인증 없이 `<img src>`로 바로 띄우기 위함입니다. 운영이라면 presigned URL이나 CDN을 앞에 둬야 합니다.

관련 이슈: [#109](https://github.com/team-basecamp/basecamp-back/issues/109) — 이미지 처리 MinIO 리팩토링

---

## 2. MinIO 없이도 되는 개발 / 반드시 필요한 개발

애플리케이션은 MinIO가 없어도 기동됩니다. MinIO 연결은 실제로 이미지를 올리거나 지울 때만 시도하기 때문입니다.

| 기능 | MinIO 없을 때 |
|---|---|
| 로그인 · 캠핑장 조회 · 예약 등 대부분의 API | ✅ 정상 |
| 이미지 **조회**(이미 저장된 URL을 응답에 실어 보내기) | ✅ 정상 (URL 문자열일 뿐) |
| 프로필 · 게시글 · 리뷰 · 캠핑장 이미지 **업로드** | ❌ 업로드 시점에 실패 |
| 이미지 **삭제/교체** | ❌ 저장소 호출 실패 |

**정리**: 이미지 업로드/수정/삭제를 건드리는 작업이라면 반드시 띄우세요. 그 외에는 없어도 개발이 됩니다.

---

## 3. 띄우기

**프로젝트 루트**(`basecamp-back/`)에서 한 줄이면 됩니다. Redis 등 다른 서비스와 함께 올라옵니다.

```bash
docker compose up -d
```

`docker-compose.yml`에 정의된 두 컨테이너가 뜹니다.

```yaml
services:
  minio:                                # 오브젝트 스토리지 본체
    image: minio/minio:latest
    container_name: basecamp-minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"                     # S3 API (서버 → SDK 접속, 이미지 URL)
      - "9001:9001"                     # 웹 콘솔 (브라우저로 버킷 들여다보기)
    volumes:
      - minio-data:/data                # 업로드한 파일이 실제로 쌓이는 곳
    restart: unless-stopped
    healthcheck: ...                    # mc ready local 로 살아 있는지 확인

  minio-init:                           # 버킷 생성 + 공개 읽기 정책만 걸고 종료되는 일회성 컨테이너
    image: minio/mc:latest
    depends_on: { minio: { condition: service_healthy } }
    entrypoint: >
      mc alias set local http://minio:9000 ... &&
      mc mb --ignore-existing local/basecamp &&        # basecamp 버킷 생성 (있으면 통과)
      mc anonymous set download local/basecamp          # URL 을 아는 누구나 GET 가능
```

- **`minio`** 는 파일을 담는 서버, **`minio-init`** 은 처음 뜰 때 `basecamp` 버킷을 만들고 공개 읽기 정책을 걸어 주는 보조 컨테이너입니다. init은 할 일을 마치면 스스로 종료됩니다(정상).
- 포트·버킷 이름·자격증명이 `docker-compose.yml`과 `application.yml`에 **같은 기본값**으로 고정돼 있어, 별도 환경변수 없이 바로 붙습니다.

---

## 4. 잘 떴는지 확인하기

### 컨테이너 상태

```bash
docker compose ps
```

`basecamp-minio`의 `STATUS`가 `Up ... (healthy)`면 정상입니다. `basecamp-minio-init`은 `Exited (0)`으로 보이는 게 정상입니다 — 버킷을 만들고 끝난 것입니다.

### 웹 콘솔로 눈으로 확인

브라우저에서 **http://localhost:9001** 접속 → **basecamp / basecamp123** 로그인.
왼쪽 **Object Browser → basecamp** 버킷에 업로드한 이미지가 `profiles/`, `posts/`, `reviews/`, `camps/` 접두어 아래로 쌓입니다.

### 업로드가 실제로 되는지

서버(`./gradlew bootRun`)를 띄우고 이미지 업로드 API(예: 프로필 수정 `POST /api/v1/users/me`, 캠핑장 등록 `POST /api/v1/camps/register`)를 호출한 뒤, 응답에 담긴 이미지 URL을 브라우저에 그대로 붙여 넣어 이미지가 뜨면 끝입니다.

```
http://localhost:9000/basecamp/profiles/9f8e....png
```

인증 없이 바로 열리는 이유가 3절의 `anonymous set download`입니다.

---

## 5. BaseCamp가 MinIO를 어떻게 쓰는가

### 저장 흐름

1. 컨트롤러가 `multipart/form-data`로 파일을 받는다 (JSON은 `request` 파트, 파일은 `image`/`images` 파트).
2. 서비스가 `FileStorageService.store(...)`로 저장소에 올린다. 파일명은 원본을 믿지 않고 **UUID로 새로 만들어** 카테고리 접두어 아래에 놓는다(`camps/ab12….jpg`).
3. 저장소는 **공개 URL과 객체 키(object key)** 를 함께 돌려준다(`StoredObject`). URL은 DB/응답에 싣고, 키는 나중에 지울 때 쓴다.

### 두 종류의 이미지 — 이게 핵심

`images` 테이블 한 곳에 성격이 다른 두 종류가 섞여 있고, `storage_type` 컬럼으로 구분합니다.

| storage_type | 무엇 | object_key | 삭제 시 |
|---|---|---|---|
| `MINIO` | 우리가 저장소에 올린 파일 | 있음 | 참조가 끊기면 **저장소 실물까지** 지운다 |
| `EXTERNAL` | 소셜 로그인이 준 프로필 URL 등 남의 서버 이미지 | `null` | DB 행만 지운다 (남의 것이라 저장소에서 지울 게 없다) |

이 구분이 없으면 외부 URL을 지우려 시도하거나, 반대로 우리 객체를 저장소에 고아로 남기게 됩니다. 삭제는 URL을 파싱하지 않고 **`object_key`로만** 합니다(`Image.isStoredByUs()`로 MINIO만 걸러냄).

### 트랜잭션과 저장소를 맞추는 법

저장소는 DB 트랜잭션에 참여하지 않습니다. 그래서:

- **외부 IO(업로드)는 짧은 쓰기 트랜잭션과 분리**합니다. 캠핑장은 `CampTransactionService`라는 별도 빈으로, 프로필/게시글/리뷰는 `TransactionSynchronizationManager` 훅으로 처리합니다.
- **정리 시점은 커밋 이후**입니다. 커밋되면 교체·삭제로 밀려난 옛 객체를, 롤백되면 방금 올린 새 객체를 지웁니다. 커밋 전에 지우면 롤백 시 DB에 살아 있는 이미지의 실물이 사라져 깨진 링크가 됩니다.

### 소셜 로그인 vs 직접 올린 프로필

사용자가 직접 올린 프로필(`MINIO`)은 다음 소셜 재로그인 때 **소셜 URL로 덮어쓰지 않습니다.** 덮으면 사용자가 고른 이미지가 사라지고 저장소 객체가 고아로 남기 때문입니다. 현재 프로필이 `EXTERNAL`(또는 없음)일 때만 소셜 URL을 동기화합니다.

---

## 6. 환경변수

`src/main/resources/application.yml`의 기본값이 `docker-compose.yml`과 맞춰져 있어, **로컬에서는 아무것도 설정하지 않아도 됩니다.**

```yaml
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}         # 서버 → MinIO SDK 접속 주소
  public-endpoint: ${MINIO_PUBLIC_ENDPOINT:http://localhost:9000}  # DB/응답 URL 의 접두어(클라이언트가 접속할 주소)
  access-key: ${MINIO_ROOT_USER:basecamp}
  secret-key: ${MINIO_ROOT_PASSWORD:basecamp123}
  bucket: ${MINIO_BUCKET:basecamp}
  max-count: ${IMAGE_MAX_COUNT:10}                          # 한 요청당 첨부 이미지 최대 개수
  allowed-extensions: jpg,jpeg,png,gif,webp                 # 허용 확장자
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MINIO_ENDPOINT` | `http://localhost:9000` | 백엔드가 SDK로 붙는 주소 |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | DB에 저장되는 이미지 URL의 접두어. 서버와 클라이언트가 보는 주소가 다를 때(도커 네트워크 등)만 나눠서 준다 |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | `basecamp` / `basecamp123` | 콘솔 로그인 = SDK 자격증명 |
| `MINIO_BUCKET` | `basecamp` | 버킷 이름 |
| `IMAGE_MAX_COUNT` | `10` | 한 요청 첨부 상한 |

> `endpoint`와 `public-endpoint`를 나눈 이유: 서버가 도커 네트워크 안에서 `http://minio:9000`으로 붙더라도, 브라우저에 내려줄 URL은 `http://localhost:9000`이어야 하기 때문입니다. 로컬 단독 개발에서는 둘 다 `localhost:9000`이라 신경 쓸 일이 없습니다.

---

## 7. 자주 쓰는 명령어

모두 **프로젝트 루트**에서 실행합니다.

```bash
# 띄우기 (redis 등과 함께)
docker compose up -d

# 상태 확인
docker compose ps

# 로그 보기
docker compose logs -f minio

# 멈추기 (데이터는 남음)
docker compose stop

# 완전 삭제 — ⚠️ minio-data 볼륨까지 지워 업로드한 이미지가 전부 사라진다
docker compose down -v
```

### 버킷 안을 CLI로 들여다보기

```bash
# mc 별칭 등록 (한 번만)
docker compose exec minio mc alias set local http://localhost:9000 basecamp basecamp123

# 버킷 안 파일 트리로 보기
docker compose exec minio mc ls --recursive local/basecamp

# 공개 정책 확인 (download 로 나와야 URL 직접 열람 가능)
docker compose exec minio mc anonymous get local/basecamp
```

---

## 8. 문제 해결

### 업로드는 됐는데 이미지 URL이 안 열린다 (AccessDenied)

버킷 공개 정책이 안 걸렸습니다. `minio-init`이 제대로 돌았는지 확인하고, 필요하면 직접 겁니다.

```bash
docker compose logs minio-init          # 'bucket ready: basecamp' 가 보여야 정상
docker compose exec minio mc anonymous set download local/basecamp
```

### `basecamp-minio-init` 이 Exited 상태다

**정상입니다.** 버킷 생성/정책 설정만 하고 스스로 종료되는 일회성 컨테이너입니다. `Exited (0)`이면 성공, `Exited (1)` 등 0이 아니면 `docker compose logs minio-init`으로 원인을 봅니다.

### 서버 로그에 `IMAGE_UPLOAD_FAILED` / 연결 실패

MinIO가 안 떠 있거나 주소가 다릅니다. `docker compose ps`로 `basecamp-minio`가 `healthy`인지 보고, 9000 포트가 열려 있는지 확인하세요.

### `INVALID_IMAGE_TYPE` 로 막힌다

확장자·Content-Type이 허용 목록(`jpg,jpeg,png,gif,webp`) 밖이거나, 확장자만 이미지로 바꾼 위조 파일입니다. 서버가 실제 바이트를 디코딩해 한 번 더 검증합니다(webp는 JDK 리더가 없어 이 디코딩 검증만 제외).

### `port is already allocated` (9000 / 9001)

이미 다른 프로세스가 그 포트를 쓰고 있습니다(예전에 직접 띄운 MinIO 등).

```bash
docker ps -a --filter "publish=9000"
docker rm -f <나온_컨테이너_이름>
docker compose up -d
```

### 업로드한 이미지가 다 사라졌다

`docker compose down -v`로 `minio-data` 볼륨을 지웠을 때 그렇습니다. `-v` 없이 `down`하면 볼륨은 남습니다. 로컬 개발 데이터라 다시 올리면 됩니다.

---

## 참고

- [docker-setting.md](docker-setting.md) — Docker 설치와 기본 사용법
- [이슈 #109](https://github.com/team-basecamp/basecamp-back/issues/109) — 이미지 처리 MinIO 리팩토링
- MinIO 콘솔: http://localhost:9001 (basecamp / basecamp123)
