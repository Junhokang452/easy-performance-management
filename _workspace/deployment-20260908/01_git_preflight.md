# easy-performance-management GitHub 반영 전 read-only preflight

**일시**: 2026-09-08  
**범위**: 현재 worktree, README/Dockerfile, GitHub Actions, `lib/easy-platform` submodule, 비밀·증거 산출물 위험  
**수행하지 않음**: stage/commit/push/fetch/deploy/제품 수정

## 결론

**현재 상태로 부모 저장소를 바로 commit/push하면 안 된다.** 가장 큰 차단은 제품이 직접 사용하는 공유 UI/XLSX 자산이 dirty detached submodule에만 있고, 부모가 가리키려는 커밋이 GitHub에 없다는 점이다. submodule 의존을 먼저 격리 commit/push한 뒤 부모 gitlink를 고정해야 한다.

두 가지 스테이징/컨텍스트 누출 위험도 commit 전 해소해야 한다.

1. Windows/UNC 작업으로 `backend/gradlew` 실행 비트가 `100755 → 100644`로 변경된 상태이다.
2. `.gitignore`가 `_workspace/`, `output/`, `__pycache__/`, `*.pyc`를 제외하지 않아 `git add -A`는 증거·PNG·PDF·runtime JSON·Python bytecode를 함께 stage한다.

## 현재 Git 상태

- 부모 repo: `main`, `HEAD=1d9d282`, `origin/main=1d9d282`, staged 변경 0
- remote: `https://github.com/Junhokang452/easy-performance-management.git`
- 추적 변경: 80 files, `+1766/-1592` (이 중 `lib/easy-platform` gitlink 1)
- untracked: 732 paths
  - `_workspace`: 457
  - `output`: 4
  - backend: 174
  - frontend: 62
  - scripts: 34
  - README: 1
- 추적 중인 기존 `_workspace` 파일은 32개이지만, 이번 457개는 모두 untracked이며 현 제품 commit 범위에서 제외하는 것이 안전하다.
- 상태에 이전 전체-cycle, S1~S4, Boot 4.1.1 이전 등이 함께 축적돼 있어 최종 반영은 작은 S4 commit만이 아니라 현 제품 baseline 전체를 포함해야 한다.

## commit/push 가능 제품 범위

submodule 차단을 먼저 해소한 뒤 다음 범위는 제품 GitHub 반영 후보다.

- repo 메타: `.gitignore`, `README.md`, `Dockerfile`
- backend: `backend/build.gradle.kts`, Gradle wrapper, `backend/src/main/**`, `backend/src/test/**`
- DB: `V20260907_001..007`, `V20260908_001..003` 신규 Flyway migration
- PDF: NanumGothic font, OFL/README, PDFBox 3.0.8 의존, S4 코드/테스트
- frontend: `frontend-vite/package*.json`, config, `public/`, `scripts/`, `src/`
- 운영/검증 코드: `scripts/` 하위 `.sh`, `.py`, `.cjs`, `.sql` 소스
- shared dependency: **push된 submodule commit을 가리키는** `lib/easy-platform` gitlink

다음은 제외한다.

- `_workspace/**` 신규 증거, runtime metadata, OpenAPI/JSON, 캡처, 로그, PowerShell harness
- `output/**` PDF/PNG 렌더 산출물
- `scripts/**/__pycache__/**`, `*.pyc`
- `.local-demo/**` DB, JAR, PID, logs, `secrets.env` (현 `.gitignore`에서는 정상 제외)
- 임시 Windows 검증 사본/JDK/build output

`git add -A` 또는 repo 루트 통째 staging은 금지하고, 명시 allowlist로 stage한 뒤 staged diff에 제외 경로가 0인지 재확인해야 한다.

## submodule 차단

- 부모 index gitlink: `172e5ed`
- 현 submodule HEAD: `b1654e937b781676eda13f57b7e344548fa44013` (detached HEAD)
- root의 인증 GitHub 확인: `b1654e9` remote 미존재
- submodule worktree: 15 dirty entries
  - 제품이 사용하는 untracked `MasterDetailWorkspace` 자산
  - 제품 backend XLSX가 import하는 untracked `com.easyware.platform.spreadsheet.SimpleXlsx`
  - UI package export/package.json 수정
  - Windows mode drift 5개: submodule `gradlew` + validation shell scripts `100755 → 100644`
- 부모 Docker build는 submodule 디렉터리를 그대로 COPY하므로 로컬 dirty tree에서는 성공해도 GitHub fresh clone에서는 UI/XLSX 클래스가 없어 빌드 실패한다.

**안전 순서**:

1. easy-standards 공유 main을 직접 변경하지 말고 PA 전용 브랜치에 필수 Boot 4/UI/XLSX 자산만 격리한다.
2. mode-only drift를 제외/복원하고, shared lib 자체 검증 후 commit/push한다.
3. 원격에서 새 shared commit이 조회됨을 확인한다.
4. 부모의 `lib/easy-platform` gitlink를 그 커밋으로 고정한다.
5. 신선 clone + recursive submodule로 FE build/BE test·bootJar를 재확인한 뒤 부모를 commit/push한다.

## 비밀·build context 위험

- prod/smb/application 설정은 DB/JWT/Neon/S2S 값을 environment placeholder로 받는다. 실 운영 비밀이 제품 소스에 hardcode된 근거는 발견하지 못했다.
- README/로컬 검증의 `dev` password와 S1 synthetic HMAC 값은 로컬 합성 fixture용이다. prod profile에서 재사용하지 않아야 한다.
- `.local-demo/secrets.env`는 Git에서는 ignore되지만, **`.dockerignore`에 `.local-demo/`가 없다.** 현재 `.local-demo` 안에 DB, log, PID, jar, `secrets.env`가 실재한다. 로컬/remote Docker build context에 이 디렉터리가 전송되지 않도록 commit/deploy 전 `.dockerignore`에 `.local-demo/`를 반드시 추가해야 한다.
- `.dockerignore`에 `output/`도 없으므로 렌더 산출물을 context에서 제외하는 것이 좋다.
- `.gitignore`에 `__pycache__/`, `*.pyc`, `output/`가 없어 현재 Python bytecode 11개와 PDF 4개가 untracked로 노출된다.

## README/Dockerfile/Workflow

- 신규 README는 Boot 4.1.1, Model B, 로컬 합성 데모, 5 locale, 평가 운영 흐름을 설명한다. 운영 Neon이 이미 준비됐다고 오해할 수 있어 실 배포 DB가 새로 선택/구성된 후 배포 문구와 분리해야 한다.
- Dockerfile은 Boot 4.1.1, Gradle 8.14.5 digest pin, Node 20, Java 21, non-root, healthcheck 구성이다. backend build가 `-x test`를 사용하므로 GitHub 반영 전 별도 전체 테스트 증거가 필수이다(현 로컬 동결 소스 336/336 PASS).
- repo 내 자동화:
  - `frontend-quality.yml`: `main` push 시 항상 실행
  - `check-migration-naming.yml`: `main` push 중 migration path 변경 시 실행. 현 신규 migration 10개로 트리거됨.
  - repo 내 Naver/deployment workflow는 없다.
- 따라서 main push의 repo 내 자동 영향은 FE quality + migration naming이며, Naver 배포는 별도 수동/외부 연결 절차로 봐야 한다.

## push 전 필수 게이트

- [ ] DB 선택/배포 환경값 사용자 결정
- [ ] shared lib PA 전용 commit/push 후 parent gitlink 고정
- [ ] 부모 `backend/gradlew` 100755 보존; submodule mode-only drift 제외
- [ ] `.dockerignore`: `.local-demo/`, `output/` 추가
- [ ] staging에 `_workspace`, `output`, `__pycache__`, `*.pyc`, `.local-demo` 0개
- [ ] staged diff에 실 DB URL/비밀/JWT/S2S token 0개
- [ ] recursive fresh clone에서 submodule init, FE quality, BE 전체 test/bootJar
- [ ] GitHub Actions 2개 PASS
- [ ] 그 뒤에만 Naver 수동 배포; health/auth/tenant/Flyway 검증

## 즉시 판정

- GitHub commit/push: **BLOCKED** — shared dependency remote 미존재 + dirty detached submodule + wrapper mode drift
- Naver deploy: **BLOCKED** — 사용자 DB 선택 대기 + GitHub/source dependency gate 미통과
- 현 작업에서 외부 쓰기: **0건**
