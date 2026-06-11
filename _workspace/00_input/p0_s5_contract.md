# P0-S5 계약서 — PerformanceReport append-only + HR 일괄 발행 + 사원 결과 조회

> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / BE·FE 에이전트 공동 SoT — **이탈 금지**
> ERD SoT: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md` Entity 18 (line 114~117) + 화면 #7 `/my/report` (line 230) + #26 `/hr/reports` (line 264)
> 결정 정합: **G_PERF_E9 = 본인 결과 + 분포 % 만** (타인 점수·서열 비노출 — 부서 분포는 P0-S6 rm_org 수신 전이므로 **전사 분포 % 로 대체 박제**, 건수 비노출 = 소규모 집단 역추적 방지)
> 선행 의존: P0-S3 reviews (FINALIZED + finalGrade/finalScore/kpiScoreDetail) + P0-S1 cycle 상태기계
> Appeal 신청 링크는 P0-S10 스켈레톤 후 — 본 슬라이스 비노출 박제.

## 0. 설계 결정 3건 (checkpoint 질문 고정)

1. **발행 모델**: cycle 단위 **일괄 발행** (`publish` — FINALIZED review 중 active report 미존재분만 생성, published/skipped 카운트) + **개별 재발행 = supersede 체인** (KpiActual 패턴 동형: 신규 row + `supersedes_id` UNIQUE — 정정 시 content 재동결). DELETE 경로 없음 (append-only — cycle CASCADE 만).
2. **content jsonb 동결 스냅샷 shape** (§5): review 확정값 + 발행 시점 전사 분포 %. `nextAction` 키는 null 박제 (P1).
3. **mutable 예외 2 필드 한정**: `viewed_at` (최초 1회 set, 멱등) + `acknowledged`/`acknowledged_at` (true 단방향, 멱등) — content·publishedAt 등 본문은 불변. view/acknowledge/supersede 는 **active 행 한정** (superseded 행 → E9804934).

## 1. Entity 1건

### PerformanceReport (`performance_report`) — append-only
- `id` UUID PK (UuidV7) / `tenant_id` (TenantAwareAuditEntity)
- `cycle_id` UUID NOT NULL FK → evaluation_cycle **ON DELETE CASCADE** (ERD 외 additive — 조회 효율, P0-S4 applied_at/by additive 전례)
- `review_id` UUID NOT NULL FK → performance_review **ON DELETE CASCADE**
- `employee_id` UUID NOT NULL (plain UUID)
- `published_at` timestamptz NOT NULL / `published_by` UUID NULL (additive audit)
- `content` jsonb NOT NULL — §5 동결 스냅샷 (P0-S1 D2 JSONB 패턴)
- `viewed_at` timestamptz NULL
- `acknowledged` boolean NOT NULL DEFAULT false / `acknowledged_at` timestamptz NULL (additive)
- `supersedes_id` UUID NULL **UNIQUE** FK → performance_report(id) (체인 선형성)
- 인덱스: `(tenant_id, cycle_id, employee_id)` + `(tenant_id, review_id)`
- UPDATE 는 viewed_at/acknowledged/acknowledged_at 3 컬럼 외 금지 (service 레벨 강제 — setter 미노출)

## 2. Flyway

`backend/src/main/resources/db/migration/V20260611_005__performance_report.sql` — V20260611_004 의 audit·jsonb DDL 패턴 복제.

## 3. 게이트

| 동작 | 조건 | 위반 코드 |
|------|------|----------|
| publish (일괄) / supersede (개별 재발행) | `cycle.status == FINALIZED` | E9804252 (422) |
| publish 대상 | review.status == FINALIZED **AND** active report 미존재 (그 외 skip 카운트 — 에러 아님) | — |
| view / acknowledge / supersede 대상 | active 행 (superseded 아님) | E9804934 (409) |
- active = 다른 행의 supersedes_id 로 참조되지 않은 행 (KpiActualResponse `superseded` computed 패턴)

## 4. ErrorCode 3건

| 규칙 | ErrorCode | HTTP |
|------|-----------|------|
| report 없음 (my 포함) | REPORT_NOT_FOUND `E9804449` | 404 |
| cycle.status != FINALIZED 에서 publish/supersede | REPORT_CYCLE_NOT_FINALIZED `E9804252` | 422 |
| superseded 행에 view/acknowledge/supersede | REPORT_NOT_ACTIVE `E9804934` | 409 |
- review 미존재 = 기존 `REVIEW_NOT_FOUND E9804447` / cycle 미존재 = `CYCLE_NOT_FOUND E9804441` 재사용 (FE errorMapping 포함 5 키)

## 5. content 동결 스냅샷 shape (camelCase — BE 가 발행 시점에 생성, 이후 불변)

```
ReportContent {
  finalGrade: string | null,        // review 확정값
  finalScore: number | null,
  kpiScore: number | null,
  mboScore: number | null,          // P0 = null
  competencyScore: number | null,   // P0 = null
  mraScore: number | null,          // P0 = null
  managerComment: string | null,    // selfComment 은 비포함 (화면 #7 사양)
  kpiItems: ReviewKpiItemResponse[] | null,  // review.kpiScoreDetail 전체 사본 (이미 동결 스냅샷)
  distribution: {S,A,B,C,D: number} | null,  // 발행 시점 cycle 의 FINALIZED review finalGrade 분포 "비율" (0~1, round 4)
                                             // — 건수 비노출 (E9). 분모 = FINALIZED 수. UNRATED 없음 (FINALIZED 는 grade 보유)
                                             // finalGrade null FINALIZED 행(다른 scale)은 분모 포함·버킷 제외 박제
  nextAction: null                   // P1 박제
}
```
- supersede 시 content 는 **재생성** (review 현재 확정값 + 최신 분포로 재동결)
- 분포 버킷 카운트는 ReportService 내 단순 구현 (CalibrationService 의 effectiveGrade 분포와 다름 — 여기는 FINALIZED·finalGrade 만. 중복 아님을 보고서에 박제)

## 6. REST 계약 (base `/api/v1`)

| HTTP | Path | Body | 응답 |
|------|------|------|------|
| GET | `/cycles/{cycleId}/reports?employeeId=` | — (employeeId 옵션 필터) | `List<ReportResponse>` (superseded 포함 전부 — HR 이력 가시성, publishedAt DESC) |
| POST | `/cycles/{cycleId}/reports/publish` | `ReportPublishRequest {actorEmployeeId?}` | `ReportPublishResponse {publishedCount, skippedCount, published: ReportResponse[]}` |
| GET | `/reports/{reportId}` | — | `ReportResponse` |
| GET | `/reports/my?cycleId=&employeeId=` | — (둘 다 필수) | `ReportResponse` (**active 행만**, 없으면 404 E9804449) |
| POST | `/reports/{reportId}/view` | — | `ReportResponse` (viewed_at 최초 1회 set — 이미 있으면 no-op 멱등) |
| POST | `/reports/{reportId}/acknowledge` | `ReportAcknowledgeRequest {actorEmployeeId?}` | `ReportResponse` (acknowledged=true + acknowledged_at — 멱등) |
| POST | `/reports/{reportId}/supersede` | `ReportSupersedeRequest {actorEmployeeId?}` | 201 `ReportResponse` (신규 row, supersedesId=원본, content 재동결) |

### Response shape (camelCase JSON)

```
ReportResponse {id, cycleId, reviewId, employeeId, publishedAt, publishedBy,
                content: ReportContent,            // 파싱된 객체 (P0-S3 kpiScoreDetail 노출 패턴)
                viewedAt, acknowledged, acknowledgedAt,
                supersedesId, superseded,          // superseded = computed (다른 행이 나를 참조)
                createdAt}
```

## 7. FE 화면 2종 (#26/#7)

| 화면 | 라우트 | 내용 |
|------|--------|------|
| HR 리포트 발행 (#26) | `/hr/reports` | cycle Select(CycleSelect 재사용) → 상단 요약(FINALIZED review 수 — useReviewsQuery 재사용·클라이언트 필터 vs 발행 완료 수) + **일괄 발행** 버튼(확인 모달 — cycle FINALIZED 아닐 때 안내 + 결과 published/skipped notification) + 발행 현황 테이블(employeeId·finalGrade GradeBadge 재사용·publishedAt·viewed/acknowledged 상태 아이콘·superseded Badge) + 행별 **재발행**(supersede confirm 모달 — "content 재동결" 경고) |
| 사원 본인 리포트 (#7) | `/my/report` | cycle Select + employeeId 입력(MyKpiPage 패턴) → `GET /reports/my` (404 = 빈 상태 "리포트가 아직 발행되지 않았습니다") → **리포트 카드**: finalGrade 대형 Badge + 점수 분해 4종(kpi 값·mbo/competency/mra 는 "—" P1 안내) + KPI 항목 요약 표(content.kpiItems read-only — ReviewKpiItemsTable 재사용 가능하면 재사용) + managerComment + **전사 분포 % 가로 막대**(content.distribution — P0-S4 DistributionBars 재사용 또는 비율 전용 경량 변형, 본인 등급 강조) + **열람 자동 기록**(조회 성공 시 view 호출 1회 — viewedAt null 일 때만) + **확인(acknowledge) 버튼**(멱등, acknowledged 후 비활성 + 확인 시각 표시). Appeal 링크 비노출 (P0-S10) |

공유 컴포넌트: `pages/report/` 아래 ReportCard(또는 페이지 내 구성 자유), errorMapping.ts (신규 3 + 재사용 447/441 = 5 키). API 모듈 `src/api/reports.ts` (reportsQueryKeys + RQ 훅 — calibration.ts 패턴 정합). GradeBadge·DistributionBars·CycleSelect·ReviewKpiItemsTable 은 기존 것 재사용 우선 (변형 필요 시 보고서 박제).

i18n: ko + en 풀 — `nav.*` 2키 + `report.*` + `error.E9804449/252/934` 3키 parity.

주의: 분포·점수는 BE content 표시만 (FE 재계산 금지 — % 환산 렌더 한정). view 자동 호출은 무한 재호출 금지 (viewedAt null && 최초 로드 시 1회 — useEffect 가드).

## 8. 게이트

- BE: `gradlew compileJava` + `gradlew test` 전체 (기존 135 회귀 0 + 신규 ≥12)
- FE: `npm run typecheck` + `npm run build` (lazy 2 chunk 분리 확인)
- 표준: STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY + ADR-026 명명 + ADR-027 i18n
- legacy selfevaluation·talent 채널 비접촉
