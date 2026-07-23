# Docker / Redis 로컬 세팅 가이드

> Docker와 Redis를 처음 다루는 팀원을 위한 문서입니다. 순서대로 따라 하면 됩니다.

## 목차

1. [왜 필요한가](#1-왜-필요한가)
2. [Redis 없이도 개발은 됩니다](#2-redis-없이도-개발은-됩니다)
3. [Docker 설치](#3-docker-설치)
4. [Redis 띄우기](#4-redis-띄우기)
5. [잘 떴는지 확인하기](#5-잘-떴는지-확인하기)
6. [BaseCamp가 Redis를 어떻게 쓰는가](#6-basecamp가-redis를-어떻게-쓰는가)
7. [자주 쓰는 명령어](#7-자주-쓰는-명령어)
8. [문제 해결](#8-문제-해결)

---

## 1. 왜 필요한가

BaseCamp는 로그인 상태를 **JWT(access token)** 로 관리합니다. 이 토큰은 서버에 저장하지 않고, 서명만 맞으면 **만료 시각(기본 30분)까지 무조건 유효**합니다. 서버가 "이 토큰은 이제 무효야"라고 말할 방법이 없다는 뜻이죠.

그래서 아래 세 가지가 저장소 없이는 불가능합니다.

| 상황 | 저장소가 없으면 |
|---|---|
| 로그아웃 | 로그아웃 눌러도 그 토큰으로 30분간 API 호출 가능 |
| 회원 탈퇴 | 탈퇴 후에도 30분간 API 호출 가능 |
| 관리자 제재 | 강제 로그아웃시켜도 30분간 API 호출 가능 |

**Redis는 "무효화된 토큰 목록"을 담아두는 곳**입니다. 인증 필터가 매 요청마다 여기를 확인해서, 죽은 토큰이면 거부합니다.

> **왜 MySQL이 아니라 Redis인가?**
> 이 조회는 **모든 API 요청마다** 발생합니다. MySQL을 매번 때리면 JWT를 쓰는 이유(DB 조회 없는 빠른 인증)가 사라집니다. Redis는 메모리 기반이라 빠르고, **TTL(만료 시간)** 을 걸어두면 알아서 지워집니다.
>
> 영속 기록은 여전히 MySQL `token_blacklist` 테이블에 남습니다. Redis는 **조회용 캐시**입니다.

---

## 2. Redis 없이도 개발은 됩니다

**중요**: Redis가 없어도 애플리케이션은 정상 기동됩니다. Redis 연결은 실제로 쓸 때 시도하기 때문입니다(lazy connection).

Redis 조회에 실패하면 **fail-open**(경고 로그를 남기고 요청을 통과)합니다. Redis가 죽었다고 서비스 전체가 멈추면 안 되니까요.

| 기능 | Redis 없을 때 |
|---|---|
| 소셜 로그인 / 회원가입 | ✅ 정상 |
| 캠핑장 · 예약 등 일반 API | ✅ 정상 (인증 필터가 fail-open) |
| 토큰 재발급 (refresh) | ✅ 정상 (이 경로는 Redis를 쓰지도 읽지도 않음) |
| 로그아웃 / 회원 탈퇴 | ⚠️ 500 에러 (아래 참고) |
| 관리자 제재 / 해제 | ⚠️ 500 에러 |
| 로그아웃한 토큰 거부 | ❌ 만료(30분)까지 계속 유효 |

> **왜 로그아웃은 에러가 나나?**
> 조회 실패는 넘어가도, **쓰기 실패는 삼키면 안 됩니다.** 조용히 넘기면 "로그아웃했는데 토큰이 살아 있는" 상태가 되니까요. 그래서 Redis 쓰기가 실패하면 예외를 그대로 던지고, 트랜잭션도 함께 롤백됩니다.

**정리**: 캠핑장 조회나 예약 기능만 개발한다면 Redis 없이 작업해도 됩니다. **인증(로그아웃/탈퇴/제재) 쪽을 건드린다면 반드시 띄우세요.**

---

## 3. Docker 설치

### Docker가 뭔가요?

Redis를 직접 설치하려면 OS마다 방법이 다르고, 버전이 꼬이고, 지우기도 번거롭습니다.

Docker는 **프로그램을 통째로 담은 상자(컨테이너)** 를 실행해주는 도구입니다. `redis:7-alpine`이라는 상자를 내려받아 실행하면 끝이고, 지울 때도 상자만 버리면 컴퓨터에 흔적이 남지 않습니다.

- **이미지(image)**: 상자의 설계도 (예: `redis:7-alpine`)
- **컨테이너(container)**: 설계도로 실제로 돌아가고 있는 것

### 설치

**Windows**

1. [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) 다운로드 후 설치
   설치 중 **"Use WSL 2 instead of Hyper-V"** 는 체크된 채로 둡니다(기본값). WSL2는 Docker Desktop이 리눅스 컨테이너를 돌리기 위한 엔진이라 필요합니다. 없으면 설치 과정에서 알아서 깔아줍니다.
2. 설치가 끝나면 **재부팅**합니다.
3. **Docker Desktop 앱을 실행**합니다. (트레이의 고래 아이콘이 멈추면 준비 완료)

이후 명령은 **PowerShell, Git Bash, IntelliJ 터미널** 아무 데서나 실행하면 됩니다. 추가 설정은 없습니다.

<details>
<summary><b>WSL(Ubuntu) 셸에서 <code>docker</code> 명령을 쓰고 싶다면</b> — 필요한 사람만</summary>

WSL 셸 안에서 `docker: command not found`가 뜬다면 통합을 켜야 합니다.

1. Docker Desktop 실행 → 우측 상단 톱니바퀴(Settings)
2. **Resources → WSL Integration**
3. "Enable integration with my default WSL distro" 켜기
4. 아래 목록에서 쓰는 배포판(Ubuntu 등) 토글을 **ON**
5. **Apply & Restart**

WSL을 안 쓴다면 이 단계는 건너뛰세요.

</details>

**macOS**

[Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)을 설치하고 실행합니다. Apple Silicon(M1~)과 Intel용 설치 파일이 다르니 확인하세요. WSL 관련 설정은 없습니다.

### 설치 확인

```bash
docker --version
```

```
Docker version 29.6.1, build 8900f1d
```

이렇게 나오면 **CLI는 설치된 것**입니다. 하지만 이것만으로는 부족합니다.

### Docker Desktop이 실행 중인지 확인

Docker는 백그라운드에서 도는 **데몬(daemon)** 이 있어야 동작합니다. Docker Desktop 앱을 실행해 두지 않으면 모든 명령이 실패합니다.

```bash
docker info --format "{{.ServerVersion}}"
```

버전이 출력되면 준비 완료입니다.

---

## 4. Redis 띄우기

**프로젝트 루트**(`basecamp-back/`)에서 아래 한 줄이면 끝입니다.

```bash
docker compose up -d
```

처음 실행하면 이미지를 내려받느라 10~30초 걸립니다. `Container basecamp-redis  Started`가 뜨면 성공입니다.

### 무슨 일이 일어나나

저장소 루트의 `docker-compose.yml`에 **무엇을 어떻게 띄울지**가 적혀 있고, 위 명령이 그대로 실행해 줍니다.

```yaml
services:
  redis:
    image: redis:7-alpine        # Redis 7, alpine(가벼운 리눅스) 기반 이미지
    container_name: basecamp-redis
    ports:
      - "6379:6379"              # 내 컴퓨터의 6379 → 컨테이너의 6379
    restart: unless-stopped      # Docker Desktop 을 켤 때마다 자동으로 올라옴
    healthcheck: ...             # redis-cli ping 으로 살아 있는지 주기적으로 확인
```

- 포트·버전·컨테이너 이름이 **저장소에 고정**되어 있어 팀원마다 다르게 띄울 일이 없습니다.
- `-d`는 백그라운드 실행(터미널을 점유하지 않음)입니다.
- `application.yml`의 기본값이 `localhost:6379`라 **환경변수 설정도 필요 없습니다.**

> **한 번만 하면 됩니다.** `restart: unless-stopped` 덕분에 컴퓨터를 껐다 켜도(= Docker Desktop이 뜨면) Redis가 알아서 올라옵니다. 직접 `docker compose stop`으로 멈춘 경우에만 안 뜹니다.

> ⚠️ `docker compose`(**띄어쓰기**)는 Docker Desktop에 내장된 v2입니다. 옛날 문서에 나오는 `docker-compose`(하이픈)는 v1이라 따로 설치해야 합니다. 띄어쓴 쪽을 쓰세요.

---

## 5. 잘 떴는지 확인하기

### 컨테이너가 살아 있는지

```bash
docker compose ps
```

```
NAME             IMAGE            STATUS                   PORTS
basecamp-redis   redis:7-alpine   Up 2 minutes (healthy)   0.0.0.0:6379->6379/tcp
```

`STATUS`가 `Up ... (healthy)`면 정상입니다. 아무것도 안 나오면 안 떠 있는 것입니다.

> `(healthy)`는 `docker-compose.yml`의 healthcheck가 `redis-cli ping`으로 확인해 준 결과입니다. 뜬 직후에는 `(health: starting)`으로 잠깐 보일 수 있습니다.

### Redis가 응답하는지

```bash
docker compose exec redis redis-cli ping
```

```
PONG
```

`PONG`이 나오면 Redis가 정상 동작 중입니다.

> `docker compose exec redis`에서 뒤의 `redis`는 컨테이너 이름이 아니라 `docker-compose.yml`에 적힌 **서비스 이름**입니다.

### 애플리케이션에서 확인

`./gradlew bootRun`으로 서버를 띄우고 로그인 → 로그아웃을 한 뒤:

```bash
docker compose exec redis redis-cli keys "*"
```

```
1) "blacklist:3f2a1b0c-4d5e-6f70-8192-a3b4c5d6e7f8"
```

로그아웃하면 **access 토큰의 `jti` 하나**가 등록됩니다. (refresh 토큰도 폐기되지만 그건 MySQL에만 기록됩니다. 이유는 [6번](#6-basecamp가-redis를-어떻게-쓰는가) 참고)

남은 수명(초)도 볼 수 있습니다:

```bash
docker compose exec redis redis-cli ttl "blacklist:3f2a1b0c-..."
```

```
(integer) 1782
```

access 토큰 수명이 30분(1800초)이므로 **1800 이하**가 나옵니다.

---

## 6. BaseCamp가 Redis를 어떻게 쓰는가

### 접속 설정

`src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 1s
      connect-timeout: 1s
```

환경변수를 설정하지 않으면 `localhost:6379`를 씁니다. `docker-compose.yml`이 딱 그 주소로 띄우므로 **추가 설정이 필요 없습니다.**

### 저장되는 키

| 키 | 값 | TTL | 언제 생기나 |
|---|---|---|---|
| `blacklist:{jti}` | `1` | 그 access 토큰의 남은 수명 (≤30분) | 로그아웃, 회원 탈퇴 |
| `revoke:user:{userId}` | `1` | access 토큰 수명 (30분) | 관리자가 회원을 제재할 때 |

`jti`는 JWT마다 부여되는 고유 ID(UUID)입니다. 토큰 원문은 저장하지 않으므로, **Redis가 유출되어도 유효한 토큰이 새지 않습니다.**

TTL이 지나면 Redis가 알아서 키를 지웁니다. 만료된 토큰은 서명 검증 단계에서 이미 거부되므로 더 들고 있을 이유가 없습니다.

### 조회처가 경로마다 다릅니다

| 경로 | 조회처 | 이유 |
|---|---|---|
| access 토큰 검증 (매 요청) | **Redis만** | 빈도가 높아 DB로는 감당 불가. 캐시 미스는 "만료까지 유효"로 퇴화할 뿐 |
| refresh 토큰 재발급 | **MySQL만** | 캐시 미스가 나면 폐기된 토큰이 되살아남 → 탈취 토큰 부활. 절대 불가 |

그래서 **Redis에는 access 토큰의 `jti`만 올립니다.** refresh 토큰으로는 애초에 인증이 되지 않으므로 필터가 그 `jti`를 조회할 일이 없고, 넣어봐야 한 번도 읽히지 않는 키가 회전할 때마다 최대 14일씩 쌓일 뿐입니다. 재발급 경로가 Redis 장애에 묶이지도 않고요.

폐기된 refresh 토큰은 MySQL `token_blacklist`에만 기록되고, 재사용 탐지도 거기서 합니다.

### ⚠️ Redis의 키가 사라지면

Redis는 메모리 기반입니다. 키가 날아가면 **그동안 로그아웃한 사용자와 제재된 회원의 access 토큰이 만료(30분)까지 되살아납니다.** 인증 필터는 Redis만 조회하니까요.

| 상황 | 키가 사라지나 |
|---|---|
| `docker compose restart` / `stop` 후 `start` | 대개 **살아남습니다.** 공식 redis 이미지는 RDB 스냅샷이 켜져 있고, 종료 신호를 받으면 저장 후 내려갑니다 (TTL까지 복원) |
| `docker compose down` (컨테이너 삭제) | ❌ 사라집니다 |
| `redis-cli flushall` | ❌ 사라집니다 |
| 운영 환경 (영속성 끈 캐시 전용 Redis) | ❌ 사라집니다 |

즉 **스냅샷에 의존하지 마세요.** 캐시는 언제든 비어 있을 수 있다고 가정해야 합니다.

다만 그때도 그들의 **토큰 재발급과 소셜 재로그인은 여전히 MySQL로 막힙니다.** 노출 창은 이미 발급된 access 토큰의 남은 수명(≤30분)뿐입니다.

MySQL에서 다시 채워 넣는 기능(warm-up)은 아직 없습니다 → [이슈 #50](https://github.com/team-basecamp/basecamp-back/issues/50)

로컬 개발에서는 신경 쓰지 않아도 됩니다.

---

## 7. 자주 쓰는 명령어

모두 **프로젝트 루트**에서 실행합니다 (`docker-compose.yml`이 있는 곳).

```bash
# 띄우기
docker compose up -d

# 상태 확인
docker compose ps

# 멈추기 (컨테이너는 남음)
docker compose stop

# 다시 시작
docker compose start

# 재시작
docker compose restart

# 로그 보기 (Ctrl+C 로 빠져나옴)
docker compose logs -f redis

# 완전히 삭제 (데이터도 사라짐. 다시 만들려면 up -d)
docker compose down
```

### Redis 안을 들여다보기

```bash
# 대화형 접속 (나올 때는 exit)
docker compose exec redis redis-cli

# 저장된 키 전부 보기
docker compose exec redis redis-cli keys "*"

# 특정 키의 남은 수명(초)
docker compose exec redis redis-cli ttl "blacklist:어쩌구"

# 전부 비우기 (테스트 초기화용)
docker compose exec redis redis-cli flushall
```

> `keys "*"`는 운영 환경에서 쓰면 안 됩니다(전체 스캔). 로컬 확인용으로만 쓰세요.

---

## 8. 문제 해결

### `failed to connect to the docker API ... is the daemon running?`

**Docker Desktop 앱이 실행되지 않았습니다.** 시작 메뉴에서 Docker Desktop을 실행하고 기동이 끝날 때까지 기다리세요.

### `no configuration file provided: not found`

`docker-compose.yml`이 없는 디렉터리에서 실행했습니다. **프로젝트 루트**(`basecamp-back/`)로 이동한 뒤 다시 실행하세요.

### `docker-compose: command not found`

하이픈이 들어간 v1 명령입니다. **띄어쓴** `docker compose`를 쓰세요.

### WSL 셸에서 `docker: command not found`

Docker Desktop이 그 WSL 배포판과 연결되어 있지 않습니다. [3절의 WSL Integration](#3-docker-설치)을 켜거나, 그냥 **PowerShell이나 IntelliJ 터미널**에서 실행하세요(별도 설정 없이 동작합니다).

### `port is already allocated` / `bind: address already in use`

6379 포트를 이미 다른 프로그램이 쓰고 있습니다. 대개 **예전에 `docker run`으로 직접 띄워둔 Redis 컨테이너**입니다.

```bash
docker ps -a --filter "publish=6379"
docker rm -f <나온_컨테이너_이름>
docker compose up -d
```

Redis를 로컬에 직접 설치해 둔 경우라면 그걸 써도 됩니다. 우리 앱은 `localhost:6379`만 보면 되니까요.

### 애플리케이션 로그에 `RedisConnectionFailureException`

Redis가 안 떠 있거나 포트가 다릅니다. `docker compose ps`로 확인하고, `redis-cli ping`이 `PONG`을 반환하는지 보세요.

로그아웃/탈퇴/제재가 아니라 **조회 경로**에서 이 예외가 났다면 요청 자체는 통과합니다(fail-open). 경고 로그만 남습니다.

### 회사/학교 네트워크에서 이미지 다운로드 실패

프록시나 방화벽 때문입니다. Docker Desktop → Settings → Resources → Proxies에서 설정하거나, 개인 네트워크에서 한 번 받아두세요(이미지는 한 번 받으면 캐시됩니다).

---

## 참고

- [README.md](../../README.md) — 환경변수 목록(`REDIS_HOST`, `REDIS_PORT`)
- [이슈 #39](https://github.com/team-basecamp/basecamp-back/issues/39) — Refresh Token 회전 및 토큰 블랙리스트 설계
- [이슈 #18](https://github.com/team-basecamp/basecamp-back/issues/18) — 관리자 회원 제재
- [이슈 #50](https://github.com/team-basecamp/basecamp-back/issues/50) — 만료 레코드 정리 및 Redis 캐시 재구성
