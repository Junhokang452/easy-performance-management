# S2 KPI 연계 — 표준 매핑 및 설계 게이트

작성일: 2026-09-08  
범위: `easy-performance-management`의 S2 KPI 연계 설계 검토만 수행  
상태: 설계 게이트 PASS / 구현 검증 대기

## 1. 검토 범위와 판정 원칙

이번 문서는 S2의 구현 착수 전 설계 게이트다. S1의 수신·테넌트·감사 계약과 기존 dirty/untracked 변경을 되돌리거나 재검증하지 않는다. HCM/Core Master, 공유 라이브러리, 운영·배포 환경은 범위 밖이다.

S2의 최소 목표는 이미 존재하는 PA KPI 원본과 실적 이력을 평가 프로그램 참가자에게 명시적으로 연결하고, 서버가 산출한 값과 그 근거를 고정하는 것이다. KPI 원본/실적의 수정·삭제, 프런트 계산, 암묵적인 최종점수 확정은 허용하지 않는다.

참조한 SoT와 규칙은 다음과 같다.

- `lib/easy-platform/easy-standards/00-principles/01-identity-and-data.md`: 공통 tenant 식별자, tenant 선두 인덱스, 테넌트 경계.
- `00-principles/02-security-owasp.md`, `03-performance-oom.md`, `04-observability.md`: IDOR 방지, 명시적 bounds, 고위험 행위 감사.
- `00-principles/07-frontend.md`: React Query 서버 상태 SSOT, FE 재계산 금지, 5 locale.
- `00-principles/09-database.md`, `10-appendix-spring-jpa/persistence.md`: BigDecimal/정밀도, UUIDv7, FK·transaction·version/idempotency, unpaged 전체 조회 금지.
- `00-principles/11-suite-architecture.md`: Core Master는 employee/org/assignment SoR이고 KPI 원본·실적은 PA SoR; cross-product DB join 금지; read-model/snapshot의 출처와 버전 보존.
- ADR-001/003/005/006/009/011/017: UUIDv7, tenant-leading 격리, API bounds, Flyway 명명, 숫자·FK·version, Core Master/read model, React Query.
- `_workspace/00_input/p0_s2_contract.md`: 현재 P0 KPI entity/REST shape와 MANUAL-only·append-only actual 계약.
- `_workspace/followups-20260908/s2/01_frontend_plan.md`: FE는 preview/apply의 서버 파생값을 표시만 하며 Goal API에 KPI 필드를 혼합하지 않는다는 경계.

## 2. 현재 PA 계약과 S2 결손

### 2.1 보존해야 하는 현재 계약

| 표면 | 현재 사실 | S2 보존 조건 |
|---|---|---|
| `KpiTree`/`KpiNode` | tenant-scoped KPI 트리·노드, UUIDv7, MANUAL-only(P0), target/weight 보유 | 원본을 새 연결 API가 복제하거나 자동 변경하지 않는다. |
| `KpiAssignment` | `(tenant, node, employee)` 유일 배정, effective weight/target은 서버가 결정 | 프로그램 참가자 선택만으로 “해당 employee의 모든 KPI”를 암묵 연결하지 않는다. |
| `KpiActual` | append-only, 정정은 supersede 신규 row, P0 source는 MANUAL | 원본 row UPDATE/DELETE·재정정·Excel/수동 입력 계약을 건드리지 않는다. |
| `KpiService` | tenant 검증과 effective 값·achievement를 서버 계산 | S2 응답은 계산값을 재사용하되 FE가 다시 계산하지 않는다. |
| `ReviewService` | cycle+employee로 KPI item을 읽고 manager submit 때 `kpi_score_detail`을 동결 | 기존 review snapshot을 프로그램 linkage의 충분한 근거로 간주하지 않는다. actual ID·정책 버전·hash가 부족하므로 별도 S2 evidence snapshot이 필요하다. |
| `ProgramGoal`/`ProgramCalculation` | Goal은 KPI ID가 없고, calculation은 현재 goal/submission 기반 immutable revision | S2는 Goal의 title/weight/target/점수나 현재 calculation formula를 몰래 바꾸지 않는다. KPI score mix는 이번 S2 범위 밖이다. |

### 2.2 현재 결손 및 차단 경계

현재 PA에는 ProgramParticipant/Goal과 `KpiAssignment`를 연결하는 명시적 ID, KPI actual leaf의 동결 snapshot, formula/policy version, evidence hash, preview/apply stale·idempotency 계약이 없다. `ProgramExecutionService`의 기존 계산 입력에도 KPI linkage가 없다. 따라서 `cycleId + employeeId` 또는 assignment 전체 목록으로 연결을 추론하는 구현은 S2 계약 위반이다.

현재 `latestActual` 선택 경로에는 supersede 체인의 root만 보아 successor leaf를 제외할 수 있는 결함이 식별되었다. S2 preview/snapshot은 이 공통 leaf selector가 수정되고 회귀 테스트가 통과하기 전에는 PASS할 수 없다. 또한 현재 `KpiAssignment`/`KpiNode`에는 유효기간별 historical target/weight version이 없으므로 과거 `cutoffDate`에 대한 원본 정의 복원은 불가능하다. S2는 이를 가장하지 않고, actual에는 명시적 cutoff를 적용하며 target/weight는 apply 시점에 캡처된 현재 effective 값(`capturedAt`)으로 표시·보존해야 한다.

## 3. S2 최소 설계 계약(구현 전 제안)

### 3.1 연결 범위

기본 최소안은 **한 ProgramGoal과 한 `KpiAssignment`의 명시적 1:1 evidence-only link**다. 참가자 한 명에 여러 Goal이 있으면 각 Goal이 별도로 연결될 수 있지만, S2가 자동으로 0..N assignment를 모두 선택하거나 program-level mapping을 만들지 않는다. 링크는 참가자·program·goal·assignment의 tenant-scoped 조합으로 검증한다.

이 링크는 다음을 하지 않는다.

- Goal의 `weightPercent`, `targetValue`, `status`, achievement level, 수동 입력값을 변경하지 않는다.
- `ProgramCalculation.contributions`, raw/normalized/adjusted score, grade 또는 최종 상태를 자동 변경·확정하지 않는다.
- `KpiActual`, `KpiAssignment`, `KpiNode`를 수정·삭제하거나 새 KPI 원본을 생성하지 않는다.

KPI를 `ProgramCalculation` 점수에 반영하는 것은 S2 범위 밖이다. S2 linkage는 계산 근거로만 보존하며, score mix가 필요하면 후속 별도 슬라이스에서 formula/version/weight/rounding/누락값 계약부터 승인한다.

### 3.2 preview/apply 경계

정확한 경로명은 Sol BE 계약에서 확정하되, API는 기존 Goal upsert/history와 분리된 program/participant/goal 전용 surface여야 한다.

1. HR 운영자가 특정 tenant의 `programId`, `participantId`, `goalId`, `kpiAssignmentId`, 명시적 `cutoffDate`를 지정해 preview를 요청한다. `cutoffDate`는 조직 발령 기준일과 다른 KPI 근거 기준일이다.
2. 서버는 program/participant/goal/assignment의 동일 tenant·동일 participant 소속과 프로그램 변경 가능 상태를 검증한다.
3. preview 응답은 서버가 고른 assignment/node/actual ID, actual `asOfDate`, source, effective target/weight, achievement/auto value(계산을 제공하는 경우), `capturedAt`, formula/policy version, evidence hash, warnings/status를 반환한다.
4. apply는 preview ID 또는 canonical hash를 echo하고 서버가 동일 입력을 재조회한다. 입력·근거가 달라지면 409 stale로 거부하며 partial write를 하지 않는다.
5. 성공 시 immutable linkage/evidence snapshot과 audit event를 한 transaction으로 만든다. 동일 tenant+program+goal+assignment+previewHash 재시도는 같은 결과를 반환하며 중복 snapshot을 만들지 않는다.

preview는 계산·검증만, apply가 명시적 연결과 snapshot 생성만 수행한다. preview 성공을 적용 또는 점수 확정으로 해석하지 않는다.

### 3.3 기준일·actual leaf·근거

- `cutoffDate`는 조직 발령 `asOfDate`와 별도의 명시적 KPI 근거 기준일이다. `program.startsOn <= cutoffDate <= program.endsOn`이고 `cutoffDate <= today`여야 한다. `KpiActual.asOfDate <= cutoffDate`만 후보로 삼고, `asOfDate == cutoffDate`는 포함하며 미래 actual은 제외한다.
- superseded row는 제외하고, 수정 chain의 **현재 leaf**를 선택한다. 같은 assignment의 leaf 중 기준일 이하에서 `asOfDate DESC`, 동률 시 생성 시각/UUIDv7 순으로 서버가 결정한다. root-only 선택을 허용하지 않는다.
- target null/0, actual 없음, 지원하지 않는 source, 연결 대상 불일치는 `MISSING`/`BLOCKED`와 기계 판독 사유를 반환한다. 누락을 0점으로 바꾸거나 암묵적으로 통과시키지 않는다.
- 현재 assignment/node의 effective target/weight를 `capturedAt`과 함께 snapshot한다. historical definition이 없다는 사실과 기준일을 응답·감사에 남긴다. 과거 target 복원인 것처럼 표기하지 않는다.
- snapshot에는 `kpiAssignmentId`, `kpiNodeId`, selected actual ID 및 supersede provenance, actual as-of/source/value, effective target/weight, program definition revision, formula/policy version, canonical payload hash, actor/time/audit ID를 보존한다. FE는 evidence URL을 fetch하거나 hash를 재계산하지 않는다.

### 3.4 tenant·권한·bounds·재시도

- apply는 tenant-scoped HR_ADMIN/SUPER_ADMIN 등 명시된 운영자만 허용한다. participant/manager의 개인 화면은 읽기 전용이다.
- 모든 ID는 server-side `findByIdAndTenantId`/participant ownership 검사를 통과해야 한다. 다른 tenant, 다른 participant, 다른 program/goal은 정보 노출 없이 404 또는 계약된 403으로 실패하고 쓰기 0건이어야 한다.
- assignment 선택은 1개이며 request body의 null/duplicate/추가 ID를 거부한다. preview/apply는 program 전체를 스캔하지 않고 명시적 participant·goal·assignment 하나만 처리한다. 향후 list가 필요하면 `page/size`와 상한을 강제한다.
- apply에는 canonical hash/preview revision 기반 stale guard와 tenant-scoped unique idempotency key가 필요하다. 동시 apply는 한 immutable snapshot만 만들고 retry는 동일 결과를 반환해야 한다.
- audit에는 actor, tenant, program/participant/goal/assignment, selected actual, result, stale/blocked 이유, trace ID를 남긴다. PII, token, secret, raw evidence URL을 로그에 남기지 않는다.

## 4. 경계 정합성 매트릭스

| 경계 | 필수 정합성 | 현재 상태 |
|---|---|---|
| JPA ↔ migration | 신규 snapshot은 tenant 선두 인덱스, UUIDv7, FK restrict/필요한 unique, immutable/version guard와 일치 | 미구현; 구현 검증 대기 |
| KPI service ↔ S2 API | leaf actual, cutoff, current target capture, cross-tenant/participant guard가 한 service 경로를 공유 | leaf selector 결함 수정·회귀 필요; 현재 BLOCKED |
| DTO ↔ FE | preview/apply/frozen response에 ID·status·reason·version·hash·capturedAt가 명시되고 GoalResponse에 임의 KPI 필드를 추가하지 않음 | 계약 초안; FE 구현 대기 |
| API ↔ React Query | query key에 tenant/program/participant/goal 포함, mutation 후 exact keys만 invalidate, 서버 파생값 표시 | 구현 대기 |
| ProgramCalculation ↔ KPI | evidence-only이며 현재 계산식·점수·최종 상태를 변경하지 않음 | S2 범위 밖으로 고정 |
| Audit ↔ retry | preview/apply와 stale/duplicate 결과가 한 transaction·멱등 audit으로 보존 | 구현 대기 |

## 5. 독립 수용 시나리오(C01–C12)

1. C01: 유효한 동일-tenant participant/goal/assignment 1개 preview가 서버 파생값과 evidence ID를 반환한다.
2. C02: 다른 participant 소속 assignment, duplicate/null ID, goal 불일치는 4xx이고 연결 0건이다.
3. C03: actual as-of가 명시적 `cutoffDate`와 같으면 포함되고 미래 actual은 제외된다. cutoffDate의 프로그램 기간·today 검증도 통과한다.
4. C04: supersede chain의 root가 아니라 현 leaf만 선택되며 root-only 회귀가 실패한다.
5. C05: actual 없음·target null/0·source/participant 불일치는 MISSING/BLOCKED이며 0점/자동 성공으로 치환되지 않는다.
6. C06: preview 후 actual/assignment/node/program이 바뀌면 apply가 409 stale이고 snapshot/Goal 변경이 없다.
7. C07: 동일 previewHash를 동시 apply·재시도해도 snapshot/audit은 하나이고 동일 결과를 돌려준다.
8. C08: cross-tenant program/participant/goal/assignment는 404/403, 데이터 누출·부분 쓰기 0건이다.
9. C09: participant/manager의 apply는 거부되고 HR 운영자만 허용된다.
10. C10: KPI actual/assignment 원본과 수동·Excel 이력은 불변이며 S2 linkage는 append-only evidence다.
11. C11: snapshot에 actual ID, cutoff, captured target/weight, formula/policy/definition revision, hash, audit actor/time이 남는다.
12. C12: FE는 React Query typed response를 표시하고 서버가 반환한 수치를 재계산하지 않으며 5 locale·typecheck/build·API stale/denial UI를 통과한다.

## 6. 게이트 판정

### 설계

**Phase 3a design: PASS (구현 검증 pending).** 확정된 최소안은 다음과 같다: explicit ProgramGoal→KpiAssignment 1:1, 별도 `cutoffDate`, evidence-only, Goal 점수/weight/target 불변, supersede leaf, current target capture 명시, server-only 계산, tenant/auth/bounds/stale/idempotency/audit, no automatic final score.

동일 Goal의 link 교체/refresh는 기존 snapshot을 수정하지 않는다. 운영자가 이유를 입력하고 새 preview/apply를 수행하면 새 immutable revision을 만들며 이전 revision과 근거를 보존한다. KPI 값을 `ProgramCalculation`에 혼합하는 정책은 S2에서 결정하거나 구현하지 않고 out-of-scope로 둔다.

exact DTO/path는 Sol BE 계약에서 고정하되 위 invariant를 약화시키거나 `program.asOfDate`를 cutoff로 재사용해서는 안 된다. 구현자는 이 설계와 다른 의미를 임의로 확장하지 않는다.

### 구현·검증

**Implementation verification: PENDING.** 현재는 BE/FE 구현, migration, compile/test, API, browser 검증을 이 문서에서 통과로 주장하지 않는다. 특히 공통 `latestActual` leaf selector 수정 및 회귀 테스트, preview/apply transaction·idempotency·cross-tenant 테스트가 선행되어야 한다. 이후 quality gate는 `gradlew compileJava`, 전체/집중 테스트, bootJar, FE typecheck/build/lint 및 실제 API/브라우저 증거를 PASS/FAIL/BLOCKED로 분리 기록한다.

현재 PA P0 KPI 계약의 `numeric(18,4)`와 unpaged legacy list는 이번 문서에서 조용히 변경하지 않는다. S2 신규 snapshot 필드는 SoT 정밀도·bounds를 따르며, 호환성 변경이 필요하면 별도 migration/계약으로 판정한다.
