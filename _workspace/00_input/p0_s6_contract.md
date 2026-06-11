# P0-S6 계약서 — hcm S2S 수신 (core-master read-model 3종)

> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / BE 전용 슬라이스 (FE 0) — **이탈 금지**
> 모범 SoT (사본 원본): `~/code/easy-talent-management/backend/src/main/java/com/easytalent/sync/{controller/SyncReceiveController, service/ReadModelSyncService, dto/SyncDtos}.java` + `db/migration/V20260610_002` 의 rm_employee/rm_org_unit/rm_assignment DDL (talent `6022d5e`, store-hr `d95bf62` 원형)
> 경계 SoT: `EVAL_SIBLING_BOUNDARY_2026-06-11.md` 채널 #1 (hcm → performance core-master)
> **송신측 비접촉**: hcm repo 의 performance 타깃 추가는 별도 슬라이스 — 본 슬라이스는 수신측만 (게이트 미설정 503 자체 차단 = LIVE 안전)

## 1. 수신 채널 1개 (talent 4채널 중 #1 만)

- `POST /api/internal/sync/core-master` (consumes JSON)
- 가드 3중 (talent SyncReceiveController 사본):
  1. Bearer 토큰 constant-time 비교 — `performance.s2s.hcm.bearer-token` (`${HCM_S2S_BEARER_TOKEN:}`)
  2. `X-Signature` HMAC-SHA256 raw body — lib BE 15 HmacService — `performance.s2s.hcm.hmac-secret` (`${HCM_S2S_HMAC_SECRET:}`)
  3. bearer/secret 미설정 시 **503 SYNC_NOT_CONFIGURED** (호출 자체 차단 — 보안 critical fail-fast)
- raw body 검증을 위한 컨트롤러 시그니처·필터 처리 = talent 구현 그대로 (raw String 수신 후 수동 역직렬화 패턴이면 동일하게)
- tenant 해석 = performance 기존 tenant 유틸 (talent `TenantSupport.currentTenantId()` 동형 — performance 측 동등 클래스를 찾아 사용, 없으면 기존 도메인 service 가 tenantId 를 얻는 방식 그대로)
- SecurityConfig: `/api/internal/sync/**` 는 JWT filter 대상 제외 + permitAll (자체 3중 가드가 인증 담당) — talent/store-hr SecurityConfig·shouldNotFilter 패턴 확인 후 performance SecurityConfig 에 최소 변경

## 2. Payload shape (hcm `SyncBatchPayload` 동일 — talent SyncDtos **사본**, @JsonAlias 포함)

```
EmployeeUpsert  {id, employeeNo, name, status, orgUnitId(@JsonAlias homeStoreId), employmentType, sourceVersion}
OrgUnitUpsert   {id, code, name, parentId, orgType(@JsonAlias storeType), sourceVersion}
AssignmentUpsert{id, employeeId, orgUnitId, positionCode, gradeCode, jobCode, effectiveFrom, effectiveTo, sourceVersion}
CoreMasterBatchRequest  {employees[], orgUnits[], assignments[]}
CoreMasterBatchResponse {employeesApplied, employeesSkipped, orgUnitsApplied, orgUnitsSkipped, assignmentsApplied, assignmentsSkipped}
```

## 3. Read-model 3 테이블 (talent V20260610_002 **사본**) + Flyway

`backend/src/main/resources/db/migration/V20260611_006__core_master_read_model.sql`
- `rm_employee` (id PK = 소스 SoR id / tenant_id / employee_no / name / status / org_unit_id / employment_type / source_version BIGINT / synced_at / audit 4컬럼)
- `rm_org_unit` (id / tenant_id / code / name / parent_id / org_type / source_version / synced_at / audit)
- `rm_assignment` (id / tenant_id / employee_id / org_unit_id / position_code / grade_code / job_code / effective_from / effective_to / source_version / synced_at / audit)
- 인덱스: talent 사본 그대로 (tenant_id 선두 — talent DDL 의 인덱스 블록 확인 후 동일 적용)
- JPA entity 3 + repository 3 — talent rm entity 매핑 방식 사본 (id 수동 set = 소스 id, UuidV7 아님)

## 4. 멱등·정합 규칙 (talent ReadModelSyncService 사본)

- `sourceVersion` 단조: 기존 row 의 source_version 보다 **낮거나 같으면 skip** (카운트), 높으면 upsert
- `id` 또는 `sourceVersion` null 인 row 는 skip (배치 부분 실패 차단)
- ReadModelSyncService 가 rm_* 의 **단일 쓰기 경로** (다른 service 의 rm 쓰기 금지)
- 응답 = applied/skipped 6 카운트

## 5. ErrorCode 3건 (talent 동형 — E98 census 충돌 0 확인: 401 영역 101~104 사용 중·105 빈자리 / 400 영역 0xx 미사용 / 5xx 첫 진입)

| 규칙 | ErrorCode | HTTP |
|------|-----------|------|
| bearer/secret 미설정 | SYNC_NOT_CONFIGURED `E9805301` | 503 |
| bearer 불일치 / HMAC 서명 불일치 | SYNC_AUTH_FAILED `E9804105` | 401 |
| 역직렬화 불가 payload | SYNC_INVALID_PAYLOAD `E9804005` | 400 |

## 6. 설정 (application.yml)

```yaml
performance:
  s2s:
    hcm:
      bearer-token: ${HCM_S2S_BEARER_TOKEN:}
      hmac-secret: ${HCM_S2S_HMAC_SECRET:}
```
- 기본 빈 값 = 미설정 503 (게이트 OFF 기본 — LIVE/dev 무영향). application-prod.yml 에도 동일 키 placeholder 추가 여부는 기존 prod yml 의 s2s 관례 확인 후 정합 (talent 사본).

## 7. 범위 제외 (후속 박제)

- FE 변경 0 — 기존 화면 employeeId 입력 → rm_employee 선택기 개선 = **별도 슬라이스** (talent S18 `useEmployeeLabelMapQuery` 패턴)
- principal 주입 전환 (employeeId 쿼리 파라미터 → JWT identity) = 별도 후속
- rm 조회 API (`GET /rm/employees?ids=` 등) = 선택기 슬라이스에서 함께
- hcm 송신측 `hcm.s2s.easyperformance.*` 타깃 추가 = hcm repo 별도 슬라이스

## 8. 게이트

- BE: `gradlew compileJava` + `gradlew test` 전체 (기존 150 회귀 0 + 신규 ≥10 — 멱등 단조/낮은 버전 skip/null row skip/카운트/503/401 bearer/401 HMAC/400 payload)
- FE: 변경 0 확인 (git status 에 frontend-vite 비등장)
- talent/hcm/store-hr repo **비접촉** (읽기 전용 참조만)
