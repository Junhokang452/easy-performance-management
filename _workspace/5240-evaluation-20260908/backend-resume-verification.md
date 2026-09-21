# Program audit backend resume verification

작성: 2026-09-08 KST

## 결과

- 정적 계약 확인: `ProgramAuditQueryService`는 HR_ADMIN/SUPER_ADMIN만 허용하고, tenant+program 객체 범위를 확인한 뒤 tenantId+programId+선택 필터로 조회한다.
- 페이지 경계는 page >= 0, 1 <= size <= 100이며 정렬은 `createdAt DESC, id DESC`다.
- 응답 `AuditRow`는 id, participantId, eventType, reason, actorEmployeeId, createdAt만 포함한다. `detailsJson`과 tenantId는 투영하지 않는다.
- 신규 migration은 없고, 기존 `program_audit_event` 인덱스는 `(tenant_id, program_id, created_at)` 및 `(tenant_id, participant_id, created_at)` 순서다.
- API 응답 shape를 바꾸는 제품 코드 수정은 하지 않았다.

## Windows 임시 복사본 검증

- WSL 재시작 없이 `%TEMP%/easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3`에 backend와 `lib/easy-platform`을 복사했다. `.git`, `build`, `node_modules`, `_workspace`, `.gradle`은 복사에서 제외했다.
- 공식 Eclipse Adoptium API에서 portable Temurin JDK 21.0.12.1+1을 받아 해당 Gradle 프로세스에서만 `JAVA_HOME`/`PATH`로 사용했다.
- focused `ProgramAuditQueryServiceTest`: 7/7, 실패·오류·스킵 0, exit 0.
- 전체 backend: 272/272, 실패·오류·스킵 0, exit 0.
- `bootJar`: 성공, exit 0. 임시 JAR SHA-256은 `e9e676457176a165841ff71371cd68e4195f535536e488bdb2700c8e6dd7bfb3`다.
- 검증 후 원본과 임시 복사본의 `ProgramAuditController/Event/EventRepository/QueryService` 및 focused test SHA-256이 모두 일치함을 확인했다.
- 로그: `windows-focused-program-audit-test.log`, `windows-backend-full-test.log`, `windows-backend-bootjar.log`; 기계 요약: `windows-backend-verification-summary.json`.

## 준비한 실제 HTTP 검증

- 스크립트: `scripts/verify-program-audit.py`
- 검증 항목: operator 200, employee 403, cross-tenant 403/404, row 6필드 exact shape, page metadata, eventType/participantId 단독 및 복합 필터, 인접 페이지 중복 방지, createdAt/id 내림차순, size>100 400, `detailsJson`/tenantId 미노출.
- actor/participant null은 런타임 fixture가 실제로 존재할 때 `verified`, 없으면 `unverified-no-fixture`로 증거에 명시한다.
- 서버가 정지된 현재 실행은 요청 처리 전 연결 거부로 끝났다. 증거 `backend-audit-api.json`은 `passed=false`, `checks=0`, `error=URLError: local runtime unavailable`로 기록했으며 기능 실패로 계산하지 않는다.

### Windows PostgreSQL/API 우회 시도

- Windows PostgreSQL 18의 새 전용 data directory 두 개를 임시 검증 루트 아래 생성했고, 두 시도 모두 `initdb`와 127.0.0.1:55489 listen까지 성공했다.
- 1차는 `pg_ctl` 출력을 `Tee-Object`로 연결해 descendant postgres가 출력 핸들을 보유하면서 EOF를 닫지 않아 `server started` 뒤 정지했다.
- 허용된 1회 보정에서는 파이프를 제거했지만 PowerShell `Start-Process -Wait`가 종료된 `pg_ctl`만이 아니라 계속 실행 중인 descendant postgres process tree까지 기다려 같은 지점에서 정지했다.
- 두 경우 모두 DB 생성·Java 8089 시작 전이므로 제품 migration, JPA, audit API 실패는 관찰되지 않았다. 추가 재시도는 하지 않았다.
- 정리 후 최신 전용 data directory의 `pg_ctl status`는 `no server running`(exit 3), 55489/8089 listener는 없다. 기존 Windows PostgreSQL 5432/5433 listener는 각각 그대로 유지됨을 확인했다.
- 상세 기계 판독: `windows-audit-runtime-summary.json`; 로그: `windows-audit-lifecycle.log`, `windows-audit-postgres.log`, `windows-audit-pgctl-start.*.log`.

## 실행 게이트 상태

- 최초 WSL focused 명령은 2분 이상 stdout/stderr 없이 멈춰 Ctrl-C로 종료했다. 이후 Windows 임시 복사본 우회로 focused·전체 test·bootJar는 모두 통과했다.
- WSL은 재시작하지 않았다. Windows 전용 PostgreSQL 우회도 harness process-wait 단계에서 허용된 재시도를 소진했으므로 실제 HTTP 검증은 미실행이다.
- 2026-09-07의 265-test XML이 아니라 이번 Windows 임시 복사본의 272-test XML을 최신 증분 근거로 사용한다.

## WSL 복구 후 정확한 재개 순서

```bash
cd /home/samsung/code/easy-performance-management/backend
./gradlew test --tests '*ProgramAuditQueryServiceTest' --no-daemon --max-workers=1
./gradlew test --no-daemon --max-workers=1
./gradlew bootJar --no-daemon --max-workers=1
cd ..
./scripts/local-demo.sh stop
./scripts/local-demo.sh start
python3 scripts/verify-program-audit.py
```

`local-demo.sh stop`은 stale PID를 제거하고, PostgreSQL이 살아 있을 때만 빠르게 종료한다. `start`는 기존 합성 DB를 재사용하고 immutable runtime JAR로 비차단 기동한다. 외부 Neon/control plane/SMTP는 사용하지 않는다.

## 남은 위험

- 신규 audit 단위 테스트와 전체 backend/bootJar는 통과했지만 실제 PostgreSQL/HTTP 검증은 아직 실행되지 않았다.
- raw Spring Data `Page` 반환은 실행 로그의 불안정 JSON shape 경고 대상이다. 현재 FE 계약이 `content` envelope를 사용하므로 별도 합의 없이 바꾸지 않았으며, 안정 page DTO 또는 전역 `VIA_DTO`는 후속 API 계약 과제다.
- WSL 전체 프로세스 수명 문제는 Storybook 기능 결함과 분리해야 한다. 8087/5174/55487 동시 종료와 stale PID가 관찰되었다.
