# Spotless 코드 포맷 가이드

> "코드 스타일로 리뷰에서 다투지 말자"를 위한 문서입니다. 처음 다루는 팀원도 순서대로 따라 하면 됩니다.

## 목차

1. [Spotless가 뭔가요](#1-spotless가-뭔가요)
2. [딱 두 개만 기억하세요](#2-딱-두-개만-기억하세요)
3. [평소 작업 흐름](#3-평소-작업-흐름)
4. [빌드가 포맷 때문에 깨질 때](#4-빌드가-포맷-때문에-깨질-때)
5. [IntelliJ에서 편하게 쓰기](#5-intellij에서-편하게-쓰기)
6. [우리 설정 뜯어보기](#6-우리-설정-뜯어보기)
7. [규칙: 포맷 커밋과 로직 커밋을 섞지 마세요](#7-규칙-포맷-커밋과-로직-커밋을-섞지-마세요)
8. [자주 묻는 것](#8-자주-묻는-것)

---

## 1. Spotless가 뭔가요

여러 명이 한 코드베이스를 만지면 **들여쓰기, 줄바꿈, import 순서** 같은 사소한 스타일이 사람마다 달라집니다. 그러면:

- diff에 실제 변경과 스타일 변경이 뒤섞여 리뷰가 힘들어지고
- "여긴 스페이스 4칸이야 2칸이야" 같은 소모적인 논쟁이 생깁니다.

**Spotless는 코드 포맷을 자동으로 맞춰주는 Gradle 도구**입니다. 우리는 포맷 규칙을 **사람이 정하지 않고 도구가 정하게** 했습니다. 규칙은 구글이 만든 **Google Java Format**을 그대로 씁니다. 그래서 "어떻게 써야 예쁘지"를 고민할 필요가 없습니다 — 그냥 명령어 한 번 돌리면 됩니다.

> 한 줄 요약: **포맷은 논쟁 대상이 아니라 도구가 정한다.**

---

## 2. 딱 두 개만 기억하세요

프로젝트 루트(`basecamp-back/`)에서 실행합니다.

```bash
# ① 포맷을 자동으로 맞춰준다 (내 코드를 실제로 고침)
./gradlew spotlessApply

# ② 포맷이 맞는지 검사만 한다 (안 맞으면 실패)
./gradlew spotlessCheck
```

- **`spotlessApply`** — 커밋하기 전에 돌리는 것. 어긋난 포맷을 알아서 고쳐줍니다.
- **`spotlessCheck`** — 맞는지 확인만 하고 고치지는 않습니다. **`./gradlew build` 안에서 자동으로 실행**되므로, 포맷이 안 맞으면 빌드가 깨집니다.

> Windows PowerShell에서도 똑같이 `./gradlew spotlessApply` 로 실행됩니다.

---

## 3. 평소 작업 흐름

```bash
# 1. 코드 작업을 한다
# 2. 커밋 전에 포맷을 맞춘다
./gradlew spotlessApply

# 3. 바뀐 게 있으면 함께 커밋한다
git add -A
git commit -m "feat: ..."
```

습관은 하나면 됩니다: **커밋 전에 `spotlessApply` 한 번.** 이러면 빌드에서 `spotlessCheck`에 걸릴 일이 없습니다.

---

## 4. 빌드가 포맷 때문에 깨질 때

`./gradlew build`나 CI에서 이런 에러가 나면:

```
> Task :spotlessJavaCheck FAILED
The following files had format violations:
    src/main/java/.../CampService.java
  Run './gradlew spotlessApply' to fix these violations.
```

당황하지 말고 **에러 메시지가 시키는 대로** 하면 됩니다.

```bash
./gradlew spotlessApply   # 자동으로 고침
git add -A                # 고쳐진 파일을 스테이징
git commit -m "chore: spotlessApply"
```

포맷 위반은 **논리 오류가 아니라** 단순히 스타일이 안 맞는 것이므로, `spotlessApply`가 100% 자동으로 해결합니다.

---

## 5. IntelliJ에서 편하게 쓰기

매번 gradle을 돌리기 번거롭다면, 에디터가 저장할 때부터 같은 규칙으로 포맷하게 만들 수 있습니다. (선택 사항 — 안 해도 gradle로 충분합니다.)

1. **Settings → Plugins** 에서 **`google-java-format`** 플러그인 검색 후 설치, IDE 재시작
2. **Settings → Other Settings → google-java-format** 에서 **Enable** 체크
3. (선택) **Settings → Tools → Actions on Save** 에서 **Reformat code** 켜기 → 저장할 때마다 자동 포맷

> 플러그인 버전과 우리 `googleJavaFormat('1.22.0')`이 크게 어긋나면 결과가 미세하게 다를 수 있습니다. 최종 기준은 언제나 **`./gradlew spotlessApply`** 입니다. 애매하면 커밋 전에 한 번 돌려서 맞추세요.

---

## 6. 우리 설정 뜯어보기

`build.gradle`:

```groovy
plugins {
    id 'com.diffplug.spotless' version '6.25.0'
}

spotless {
    java {
        target 'src/*/java/**/*.java'   // main·test 자바 소스 전체
        googleJavaFormat('1.22.0')      // 구글 자바 포맷 규칙 적용
        removeUnusedImports()           // 안 쓰는 import 자동 삭제
        trimTrailingWhitespace()        // 줄 끝 공백 제거
        endWithNewline()                // 파일 끝을 개행으로 마무리
    }
}
```

| 항목 | 하는 일 |
|---|---|
| `googleJavaFormat('1.22.0')` | 들여쓰기·줄바꿈·import 정렬 등 전체 포맷을 구글 규칙으로 통일 |
| `removeUnusedImports()` | 사용하지 않는 `import` 를 지움 |
| `trimTrailingWhitespace()` | 줄 끝에 남은 공백 제거 |
| `endWithNewline()` | 파일 마지막 줄을 개행으로 끝맺음 |

버전(`1.22.0`)을 코드에 고정해 두었기 때문에, **팀원마다 포맷 결과가 달라지지 않습니다.**

---

## 7. 규칙: 포맷 커밋과 로직 커밋을 섞지 마세요

기능을 고치면서 동시에 파일 전체 포맷을 바꾸면, diff에서 **진짜 변경이 포맷 변경에 묻혀** 리뷰가 어려워집니다.

- 평소에는 **자기가 건드린 파일**만 `spotlessApply`가 정리하므로 문제가 없습니다.
- 만약 대규모 포맷 정리가 필요하면 **포맷만 하는 커밋**(예: `chore: spotlessApply`)으로 따로 분리하세요.

> 이 규칙은 `.claude/rules/coding-convention.md`의 "코드 포맷" 항목과 같습니다.

---

## 8. 자주 묻는 것

**Q. 포맷이 마음에 안 드는데 규칙을 바꿀 수 있나요?**
개인이 임의로 바꾸지 마세요. 포맷 통일의 이점이 사라집니다. 정말 필요하면 팀 합의 후 `build.gradle`의 spotless 설정을 바꿔야 합니다.

**Q. `spotlessApply`가 내 로직을 바꾸진 않나요?**
아니요. 공백·줄바꿈·import 정렬 같은 **표현 방식만** 바꿉니다. 코드 동작은 그대로입니다.

**Q. 테스트 코드도 대상인가요?**
네. `target 'src/*/java/**/*.java'`라서 `src/main`과 `src/test`의 자바 파일이 모두 포함됩니다.

**Q. 커밋했는데 CI에서 spotlessCheck가 깨졌어요.**
`spotlessApply`를 깜빡한 것입니다. [4번](#4-빌드가-포맷-때문에-깨질-때)대로 고쳐서 다시 커밋하세요.

**Q. 매번 돌리기 귀찮아요.**
[5번](#5-intellij에서-편하게-쓰기)의 IntelliJ 자동 포맷을 켜세요. 그래도 최종 확인은 `./gradlew spotlessApply`로.

---

## 참고

- [Spotless 공식 문서](https://github.com/diffplug/spotless)
- [Google Java Format](https://github.com/google/google-java-format)
- `.claude/rules/coding-convention.md` — 코드 포맷 규칙
- `build.gradle` — 실제 spotless 설정
