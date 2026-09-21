# S2 KPI 연계 — 독립 품질·경계 QA

작성일: 2026-09-08  
검토자: Luna / code-quality-reviewer  
범위: `easy-performance-management` S2 구현의 정적 코드·계약·FE 경계 검토  
제외: 소스 수정, dirty tree 정리, HCM/Core/운영 변경

## 1. 판정 요약

| 영역 | 판정 | 근거/제약 |
|---|---|---|
| S2 설계 invariant | PASS | ProgramGoal별 명시적 1:1 KPI assignment, evidence-only, explicit cutoff, leaf, immutable revision |
| Backend 정적 계약 | PASS | DTO/controller/service/DB shape와 tenant·object·lifecycle·stale 경계 일치 |
| Frontend 정적 계약 | PASS | API path/shape, React Query, server-derived display, operator write/read-only history, 5 locale |
| Backend 집중/전체 테스트 | PASS | Sol 최종 304/304, focused 7, bootJar SHA `8F1…FFDA` |
| FE typecheck/build | PASS | Root 직접 측정 tsc 0, production build PASS, Node 12/DS/local-UI gates PASS |
| 실제 API·DB | PASS | `s2/program-kpi-local.json`: `completed=true`, 27/27 |
| 실제 브라우저 | PASS | `s2/browser/result.json`: `passed=true`, 16/16; 5 locale×1440/390, apply/read-only/lifecycle |

정적 검토 기준으로 **제품 코드 차원의 즉시 병합 차단 결함은 확인하지 못했다**. 다만 실제 API·브라우저 검증이 끝나기 전에는 S2 전체 완료로 선언하지 않는다.

## 2. Backend 계약·보안 검토

### PASS

- `ProgramKpiEvidenceController` 경로가 계약과 일치한다.
  - 후보: `GET .../participants/{participantId}/kpi-candidates`
  - 단일 Goal preview/apply: `.../goals/{goalId}/kpi-link:preview|:apply`
  - Goal별 이력: `.../goals/{goalId}/kpi-links`
- 후보 조회는 `Pageable`과 서버 상한 100을 사용한다. preview/apply는 단일 Goal·단일 assignment로 bounded다.
- Controller에서 `ActorAccess.requireActor`, 후보/preview/apply에서 `ProgramAccess.requireOperator`를 사용한다. history는 `ProgramAccess.participant`로 HR/SUPER 또는 자기 participant/활성 reviewer의 읽기만 허용한다.
- program→participant, participant→goal, assignment→participant employee, node→tree→cycle을 tenant-scoped로 재검증한다. 임의 employee ID 승격·cross-tenant 원본 조회·다른 participant goal 연결을 허용하지 않는다.
- apply는 program pessimistic lock을 먼저 획득하고 goal/assignment/node를 locked finder로 다시 읽는다. participant는 `ACTIVE`만 허용하며, `REVIEWER/COMPLETED` submission 또는 calculation 존재 시 `PROGRAM_LOCKED`다.
- `actualCutoffDate`는 program 기간, cycle 기간, 서버 today 이하의 교집합으로 검증한다. program 조직 `asOfDate`와 분리된다.
- `KpiActualSelector`는 전체 assignment history에서 superseded leaf를 먼저 판정한 다음 cutoff를 적용한다. 따라서 cutoff 이전 root가 미래 successor 때문에 부활하지 않는다. root→successor 회귀 테스트가 추가돼 있다.
- `KpiScorePolicy`를 ReviewService와 S2가 공유한다. `KPI_ACHIEVEMENT_V1`과 nested 6dp achievement/2dp score/clamp formula가 응답 문자열과 구현에 일치한다.
- preview hash에 tenant/program/participant/goal/current evidence ID/cycle/cutoff 및 goal·program·participant·assignment·node·actual 근거와 정책 버전이 포함된다. 기존 Goal의 다른 evidence를 덮지 않고 Goal별 linear revision을 만든다.
- apply 동일 hash 재시도는 program lock 후 기존 evidence를 반환한다. 다른 hash는 이전 evidence를 `supersedesEvidenceId`로 가리키는 새 immutable row를 만든다. DB integrity conflict는 API conflict로 rollback된다.
- `ProgramKpiEvidence`는 `@Immutable`이고 repository에 update/delete 메서드가 없다. migration은 KPI assignment/actual에 FK를 두지 않아 원본 삭제 이후에도 동결 scalar/evidence ID를 보존한다.
- apply는 `ProgramGoal`, `ProgramCalculation`, participant stage/status, KPI node/assignment/actual을 변경하지 않는다. `autoScore`는 evidence-only 추천값이며 final score에 연결되지 않는다.

### 계약 경계 확인

- `ProgramKpiDtos` ↔ `goalKpiLinkage.ts`의 candidate, preview row, source snapshot, apply response 필드와 enum이 일치한다.
- FE의 history URL은 `.../kpi-links`로 controller와 일치한다.
- `KpiCandidateResponse`는 서버가 계산한 target/actual/achievement/autoScore를 제공하고 FE는 재계산하지 않는다.
- source actual은 bounded assignment history에서만 읽으며 전체 tenant scan을 하지 않는다.

## 3. Frontend 품질·경계 검토

- `goalKpiLinkage.ts`는 React Query를 통해 후보·preview/apply·history를 관리한다. query key에 program/participant/goal/cycle/cutoff가 포함된다.
- Goal/participant 선택, cycle/cutoff/assignment 선택, reason confirmation은 local UI state이고 서버 데이터 복제나 점수 계산 state가 아니다.
- apply 버튼은 READY preview와 명시적 reason을 요구한다. stale 409는 재preview 안내로 표시된다.
- 운영 write surface는 `HR_ADMIN`/`SUPER_ADMIN`에만 mount된다. Evaluation/Reviewer surface는 evidence history 읽기 전용이며 apply control을 mount하지 않는다.
- Goal의 title/weight/target/status/achievement levels와 ProgramCalculation final score UI를 KPI linkage가 변경하지 않는다.
- `ko/en/ja/zh-CN/vi`에 `program.kpiLinkage` namespace가 존재하고, Mantine 직접 import 대신 기존 UI wrapper를 사용한다.
- 실제 viewport overflow, browser request 실패, API error, locale label/레이아웃은 root의 최종 browser 검증에서 확인해야 한다.

## 4. 표준·DB 점검

- PASS: migration 명명 `V20260908_002__program_kpi_evidence.sql`, UUIDv7 entity ID, tenant 선두 index, bounded Page, JSON snapshot, append-only supersede unique guard.
- PASS: 신규 S2 API는 optional date JPQL을 사용하지 않고 cutoff를 필수로 받는다.
- PASS: KPI source/actual은 PA SoR로 유지하고 HCM/Core Master cross-DB join을 추가하지 않는다.
- PASS: evidence-only 연결은 기존 수동/Excel KPI, ReviewService 점수, ProgramCalculation을 암묵적으로 변경하지 않는다.
- 주의(비차단): migration의 inherited `created_at`/`updated_at`은 기존 PA migration convention인 `TIMESTAMP`를 따르고, 새 `captured_at`은 `TIMESTAMPTZ`로 보강됐다. SoT의 신규 시간 컬럼 권고(`timestamptz`)와 완전 일치시키려면 audit 컬럼도 별도 forward-fix에서 검토해야 하나, 이번 S2에서 기존 공통 audit 스키마를 임의 변경하지 않는 것이 안전하다.

## 5. 테스트·미검증 분류

### PASS/보고된 결과

- Sol 최종: Backend 전체 304/304, focused 7 PASS, bootJar SHA `8F1…FFDA`.
- Root 직접 측정: FE TypeScript 0, production build PASS, Node 12/DS/local-UI gates PASS.
- 소스 집중 테스트에는 corrected leaf, future successor root resurrection 방지, first apply, inactive participant 거부가 포함된다.
- Root 실제 API: `_workspace/followups-20260908/s2/program-kpi-local.json` `completed=true`, 27/27 PASS. cutoff/leaf, preview non-persist, operator/auth, bounded page, concurrent apply/replay, self/reviewer read-only, cross-tenant, stale, Goal/score 불변, Goal A refresh와 Goal B 보존, missing/zero target을 포함한다.
- OpenAPI generated artifact에는 S2 4 paths(candidate, preview, apply, goal history)이 존재하며 DTO required fields와 controller path가 일치한다. Flyway `V20260908_002` 적용 성공이 보고됐다.
- S1 regression artifact `_workspace/followups-20260908/s2/s1-regression-local.json`도 22/22 PASS로 S2 변경의 reviewer-line 회귀가 없음을 확인한다.

### 아직 미검증(PENDING)

- FE browser 최종 16/16: 5 locale×desktop/mobile, reason-required cancel, explicit apply/history, employee·manager read-only, self completion 허용, reviewer completion 후 preview/apply 409.
- Root가 desktop/mobile 및 employee 캡처를 육안 확인하고, `KPI_LINK_APPLIED` typed audit event·5 locale title mapping을 반영한 뒤 tsc/build/12 tests/DS/local-UI를 재통과했다.
- bootJar/OpenAPI hash와 실제 runtime source parity, 전용 API/PG cleanup을 최종 확인했다.

## 6. Browser verifier 정적 검토

- `scripts/verify-program-kpi-browser.cjs`의 proxy 경로, `/admin/evaluation-programs/{programId}/operations`, `/evaluations/{programId}`, reviewer route, 후보/preview/apply/history API path는 현재 controller/router와 일치한다.
- locale label, form label, READY/autoScore 응답, history read-only selector와 5 locale×2 viewport 검사 전제는 현재 FE surface와 일치한다.
- verifier는 API verifier가 만든 `fixture.completed === true`와 기존 evidence revision 2를 전제로 `saved.revision === 3`을 요구하고, 이후 self/reviewer submission을 완료시킨다. 따라서 한 번 성공한 뒤 fixture reset 없이 재실행할 수 없다(다음 revision 및 lifecycle lock에서 실패). 이는 제품 결함이 아니라 **검증 스크립트 재현성 제한**이며, 최종 증거에는 fixture reset/일회 실행 조건을 함께 보존해야 한다.
- candidate를 `nodeLabel` 첫 일치로 선택하고 candidate HTTP status·uniqueness를 별도 assert하지 않는다. 현재 synthetic fixture가 단일/고유 label이라는 전제에서는 차단 사유가 아니지만, fixture가 다중 KPI가 되면 false selection 가능성이 있어 harness 주의사항으로 남긴다.
- 브라우저 검사는 evidence-only UI·권한 표면을 확인하지만 cross-tenant, stale 409, cutoff boundary, Goal/score 불변은 API verifier의 보완 증거가 필요하다.

## 7. 최종 게이트

**정적 incremental QA: PASS.** 핵심 invariant와 DTO↔FE 경계는 계약에 맞고, 앞서 확인된 participant ACTIVE, formula 문자열, lock-first replay, captured timestamp 문제는 현재 소스에서 보강됐다.

**S2 전체 게이트: PASS.** API 27/27, Backend 304/304 + focused 7, bootJar, OpenAPI/FE tsc·build/12 tests·DS/local-UI, browser 16/16, S1 regression 22/22가 각각 통과했다. 전용 API8089/PG55489는 안전 중지했으며, 기존 dirty/untracked 전체 트리나 HCM 전체 suite를 S2 PASS로 혼합하지 않는다.
