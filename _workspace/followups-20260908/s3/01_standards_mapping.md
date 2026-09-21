# S3 책임자 독려 — 표준 매핑 및 Phase3a 독립 계약 게이트

작성일: 2026-09-08  
소유: Luna (standards / independent quality gate)  
범위: `easy-performance-management`의 S3만. 제품 소스와 공유 표준은 수정하지 않는다.

## 1. 판정 요약

**Phase3a design PASS (guarded contract); implementation verification PENDING.**

이 판정은 아래의 서버 계산·IN_APP 전용·preview/apply stale 검증·DB unique 멱등 계약을 구현자가 그대로 지킨다는 조건부 판정이다. 현재 generic notification 구현 자체를 S3 완료로 판정한 것이 아니다. 현재 구현을 그대로 S3에 연결하면 다음 이유로 **FAIL**이다.

- `ProgramNotificationService`는 요청의 `recipientEmployeeIds`를 그대로 수신자로 사용한다(`ProgramNotificationService.java:8-14`). 단계/참가자/책임자 관계를 서버에서 계산하지 않는다.
- `program_notification`에는 참가자·stage·round·action·owner·source fingerprint·dedupe key가 없고 unique 제약도 없다(`ProgramNotification.java:3-6`, `V20260907_004__evaluation_programs.sql:157-167`). 같은 독려를 재시도하면 중복 행이 생길 수 있다.
- 기존 `dispatch`는 프로그램의 모든 EMAIL `READY` 행을 조회하여 전송한다(`ProgramNotificationService.java:10`). S3 apply 뒤 이를 호출하면 선택한 독려 범위를 넘어선 외부 메일 발송이 가능하다.
- 기존 UI의 `NotificationTools`도 참가자 전체의 employee ID를 프런트에서 만들어 generic queue에 보낸다(`OperationsUtilities.tsx:~180`). S3 UI는 이 수신자 추론을 재사용하지 않아야 한다.

위 결함은 S3 구현 전 해결/격리해야 하는 계약 가드이며, 현재 코드의 과거 generic 알림 기능을 되돌리거나 대체하라는 판정은 아니다.

추가 계약 대조 finding: 현재 `s3/01_backend_contract.md`의 FEEDBACK `APPEALED` 행은 개인 owner 없음으로 적혀 있으나, 실제 controller는 `requireEmployeeActor()`를 통과시키고 service는 operator가 아니면 `FINAL_FEEDBACK` assignment를 요구한다(`EvaluationProgramController.java:72`, `ProgramExecutionService.java:138-152`). 따라서 최종 계약은 위 표처럼 assignment를 알림 owner로 사용하고, operator 권한은 수신자 fallback으로 사용하지 않는 의미로 정정되어야 한다.

## 2. 근거와 표준 SoT

### 2.1 실제 PA 상태 모델

- 프로그램은 `DRAFT/OPEN/FINALIZED/CANCELLED`, 참가자는 `ACTIVE/EXCLUDED/DELETED`, 단계는 `NOT_STARTED/READY/IN_PROGRESS/COMPLETED/SKIPPED/BLOCKED`이다(`ProgramTypes.java:9-20`). S3 대상은 `OPEN` 프로그램의 `ACTIVE` 참가자 중 현재 단계의 미완료 항목뿐이다.
- 책임자 관계는 `ProgramReviewerAssignment`의 `(participant, reviewerEmployeeId, role, round, status)`이며, 역할은 `AGREEMENT_REVIEWER`, `CHECKER`, `REVIEWER`, `ADJUSTER`, `FINAL_FEEDBACK`이다. `REVOKED` assignment는 책임자로 사용하지 않는다.
- 실제 완료 전이는 `ProgramExecutionService`가 보장한다. GOAL은 모든 goal이 `AGREED` 또는 `SELF_REPORTED`일 때, INTERMEDIATE는 `ProgramIntermediateReview=COMPLETED`, SELF_REVIEW/REVIEW는 `ProgramReviewSubmission=COMPLETED`, CALCULATION은 `ProgramCalculation=FINAL`, CALIBRATION은 해당 calculation에 연결된 `ProgramAdjustment=COMPLETED`, FEEDBACK은 `ProgramFeedback=AGREED/RESOLVED`일 때 완료된다 (`ProgramExecutionService.java:88-106,112-124,129-152`, `ProgramLifecycleService.java:14`).
- `ProgramRosterService.stageBlocker`는 그룹, stage enabled, employee weight, 필요한 reviewer role, 이전 stage 완료를 검사한다(`ProgramRosterService.java:31-41`). 이 blocker/assignment 규칙과 별도의 독려 판정을 만들지 않는다.

### 2.2 적용한 easy-standards 매핑

| SoT | S3 적용 |
|---|---|
| `00-principles/01-identity-and-data.md` §2-3 | 모든 조회/쓰기에서 `tenant_id`를 먼저 제한한다. employee UUID는 수신자 ID가 아니라 tenant 안의 불변 식별자이며, `findByIdAndTenantId`로 다시 확인한다. |
| `00-principles/02-security-owasp.md` §2, §4 | operator 기능은 서버 인가·object authorization·fail-closed이다. 프런트 버튼 숨김은 권한 검사가 아니다. recipient ID 주입과 IDOR를 거부한다. |
| `00-principles/03-performance-oom.md` §1, §5 | preview/apply/history는 bounded page 또는 명시적 hard cap을 사용한다. 전체 `findAll` 후 앱 필터, 무제한 recipient list, 무제한 dispatch를 사용하지 않는다. |
| `00-principles/04-observability.md` §2-3.3 | preview/apply/blocked/stale/duplicate를 actor·tenant·program·결과·traceId로 감사/관측한다. body·평가 의견·PII를 로그/감사 details에 넣지 않으며 신규 UI 용어는 5 locale 용어집 절차를 따른다. |
| `00-principles/09-database.md` §5, §7-8 | tenant 선두 인덱스, FK restrict/적절한 cascade, 서비스 transaction, unique idempotency key와 409 매핑을 사용한다. check-then-insert만으로 중복을 막지 않는다. |
| `10-appendix-spring-jpa/persistence.md` §4-5 | DTO/projection과 bounded `Page`, `@Version` 또는 DB lock을 사용한다. 외부 호출은 이 범위에 없으므로 DB transaction 안에 SMTP 호출을 넣지 않는다. |
| `00-principles/11-suite-architecture.md` §3-4 | PA가 보유한 program/participant/reviewer assignment/submission 상태만 소비한다. HCM/Core Master/S2S/SMTP를 새로 호출하지 않고, 기존 tenant-local read model만 사용한다. |

## 3. S3 책임자 및 완료 판정 계약

서버는 각 결과를 `(participantId, stage, action, currentRound, ownerEmployeeId, ownerKind, assignmentId-or-SELF, blockedReason, sourceFingerprint)` 형태로 만든다. `ownerEmployeeId`가 결정되지 않으면 운영자나 상사를 추정하지 않고 `UNASSIGNED/BLOCKED`로만 반환한다. 미배정 행은 알림 대상이 아니다.

| 현재 stage/action | 미완료 조건 | 실제 책임자 | 완료/제외 조건 |
|---|---|---|---|
| `GOAL` / self report | `GoalMode.SELF_REPORT`에서 ACTIVE participant의 goal 중 하나라도 `SELF_REPORTED`가 아님 | 해당 participant employee (`SELF`) | 모든 goal `SELF_REPORTED`, participant 비활성, stage `COMPLETED/SKIPPED`이면 제외 |
| `GOAL` / self preparation | goal이 `DRAFT` 또는 `RETURNED`이고 agreement request 전 | 해당 participant employee (`SELF`) | goal이 `AGREEMENT_REQUESTED`/`AGREED` 또는 self-report 완료; participant 비활성·stage 완료면 제외 |
| `GOAL` / agreement decision | goal이 `AGREEMENT_REQUESTED` | active `AGREEMENT_REVIEWER`, round 0 | 모든 goal `AGREED`; assignment `REVOKED`/부재면 `UNASSIGNED` |
| `INTERMEDIATE` | review row가 없거나 `DRAFT` | active `CHECKER`, round 0 | `ProgramIntermediateReview=COMPLETED` 또는 participant stage 완료 |
| `SELF_REVIEW` | current participant의 SELF submission이 없거나 `DRAFT` | participant employee (`SELF`) | current SELF submission `COMPLETED`; 별도 invalidated/source 불일치는 자동 수신자 추정 없이 blocked |
| `REVIEW` | current round의 reviewer assignment/submission이 완료되지 않음 | 해당 round의 active `REVIEWER` | assignment와 대응 submission이 모두 `COMPLETED`; 다음 round가 있으면 다음 round만 대상 |
| `CALCULATION` | calculation을 만들 operator action이 필요함 | **employee recipient 없음** | `ProgramCalculation=FINAL`; operator fallback/임의 HR recipient 금지 |
| `CALIBRATION` | 최신 FINAL calculation에 연결된 adjustment가 없거나 `DRAFT` | active `ADJUSTER`, round 0 | 연결 adjustment `COMPLETED`; 최신 calculation과 다른 adjustment는 완료로 보지 않음 |
| `FEEDBACK` | feedback 없음/`DRAFT` | active `FINAL_FEEDBACK`, round 0 | `DELIVERED`만으로는 participant 동의가 끝나지 않음. `AGREED/RESOLVED`이면 완료 |
| `FEEDBACK` / participant action | feedback이 `DELIVERED`이고 participant가 아직 동의하지 않음 | participant employee (`SELF`) | `AGREED` 또는 appeal `RESOLVED` |
| `FEEDBACK` / appeal | feedback이 `APPEALED` | 정확히 한 active `FINAL_FEEDBACK` assignment (operator 권한은 수신자 아님) | `RESOLVED`; 실제 코드상 operator는 직접 resolve할 수 있고 비operator는 `FINAL_FEEDBACK` assignment일 때 허용된다(`EvaluationProgramController.java:72`, `ProgramExecutionService.java:138-152`). assignment 부재/복수/inactive면 `OWNER_UNADDRESSABLE/BLOCKED`이며 임의 operator를 수신자로 만들지 않는다. |

`CALCULATION`처럼 인간 assignment가 없는 단계와 owner가 없는 상태는 “미완료 인원”으로 잘못 집계하지 않는다. 단계가 group 설정상 disabled이거나 현재 stage가 아니면 후보가 아니다. `ProgramStageStatus.NOT_STARTED`와 `READY`는 아직 해당 작업이 열린 책임 episode가 아니므로 자동 독려하지 않는다. `IN_PROGRESS`만 실제 독려 후보이며, `BLOCKED`는 blocker reason을 먼저 표시하고 명시된 사람에게만 보낼 수 없으므로 기본적으로 알림을 만들지 않는다. `COMPLETED`/`SKIPPED`는 항상 제외한다.

## 4. Preview → Apply 계약

Sol 계약의 최소 tuple을 채택한다.

```text
dedupe tuple =
  tenantId, programId, participantId, stage, action,
  currentRound, ownerEmployeeId, assignmentId-or-SELF,
  policyRevision, reminderOn(UTC date)
```

- `dueDate`와 source version은 dedupe tuple에 넣지 않는다. 대신 preview hash에 포함한다. **기존 dedupe 상태(`READY`/`ALREADY_QUEUED`)와 notification ID는 preview hash에서 제외한다.** 따라서 같은 원본의 새 idempotency key/동시 요청은 source가 unchanged이면 stale이 아니라 `ALREADY_QUEUED`/`duplicateSuppressed`가 된다. 실제 업무 source(상태·assignment·owner·정책·UTC scope)가 바뀐 경우에만 apply가 409 stale로 종료된다. 자동 scheduler는 추가하지 않는다. 사용자에게 “하루 한 번” 정책의 확인이 필요한 경우에도 기본값은 이 fail-closed 정책으로 고정한다.
- `policyRevision`은 메시지/독려 정책의 명시 버전이다. 임의 subject/body와 recipient 목록을 S3 request에 받지 않는 것은 **현재 확정 계약이 아니라 보안 권고**이며, Sol의 최종 DTO에서 server-owned template/key와 함께 확정해야 한다. 최소 안전값은 S3 channel `IN_APP` 고정, external EMAIL/dispatch 필드 비노출이다.
- preview는 server가 현재 `OPEN + ACTIVE` 후보를 계산하고 `previewHash`, `sourceVersion`/row versions, owner, task summary, blocked/duplicate reason, bounded counts를 반환한다. hash 입력은 source/scope/policy/date만 포함하며 dedupe 조회 결과와 notification ID는 포함하지 않는다. 평가 점수·답변·자유 서술은 preview/알림 body에 포함하지 않는다.
- apply는 `previewHash`, 명시적 operator reason, 제한된 selected item keys 및 client idempotency key만 받는다. 서버가 다시 계산하고 hash를 비교한다. mismatch는 `409 STALE_PREVIEW`이며 일부 행만 몰래 발송하지 않는다.
- apply의 output은 `queued`, `duplicateSuppressed`, `blocked`, `unassigned`, `stale`를 분리한다. 기존 generic `NotificationQueueResponse(queued,status,notificationIds)`만으로는 이 의미를 표현할 수 없으므로 S3 전용 DTO가 필요하다.

## 5. 멱등성·동시성·상태 보존

1. 같은 tuple과 같은 UTC `reminderOn`의 재시도는 기존 `program_notification`/S3 reminder item을 반환하고 새 알림 row를 만들지 않는다. DB unique constraint가 최종 방어선이며, Java check-then-insert만 사용하지 않는다.
2. 동시에 두 operator가 apply하면 program/participant 상태를 일관된 순서로 잠그고, **lock 후 replay를 먼저 확인**한다. 그래도 unique 충돌이 나면 현재 PostgreSQL transaction은 aborted 상태이므로 같은 transaction에서 repository 조회로 복구하지 않는다. unique violation은 transaction rollback 후 409/retryable conflict로 처리하는 최후 방어선이다. 둘 다 새 알림을 만들거나 실패한 transaction에서 조회를 이어 가는 방식은 금지한다.
3. source fingerprint에는 participant rowVersion, 현재 stage/status/round, active assignment id·rowVersion·status, relevant goal/submission/adjustment/feedback revision/status, program definition/policy revision을 포함한다. dedupe 상태와 notification ID는 source fingerprint에 포함하지 않는다. preview 이후 완료/assignment 변경/participant 제외가 일어나면 409 또는 명시적 `SKIPPED_COMPLETED`로 fail-closed 하고, 기존 동일 dedupe row만 발견된 경우에는 `ALREADY_QUEUED`/`duplicateSuppressed`로 처리한다.
4. 알림은 immutable source snapshot을 보유해야 한다. 현재 `ProgramNotification`에는 source/owner/stage/dedupe 필드가 없으므로, additive reminder item/snapshot 또는 동등한 nullable 확장과 migration이 필요하다. 기존 generic 알림의 기록을 덮어써서 S3 provenance를 만들지 않는다.
5. IN_APP `SENT`는 DB 알림함에 저장되었다는 의미이며 실제 열람은 `READ`/`readAt`이다. S3 apply 성공을 업무 완료나 reviewer finalization으로 해석하지 않는다. `ProgramNotificationService.read`의 tenant+recipient 확인 패턴을 유지한다.

### 5.1 source writer lock 상태 (기존 finding 해소, runtime 재검증 대기)

이전 초안의 “plain `requireOpen` 때문에 source writer가 program lock을 우회한다”는 finding은 현재 코드에서 해소되었다. `ProgramExecutionService.requireOpen`이 `requireOpenLocked`로 위임하고, `requireOpenLocked`가 tenant-scoped `findLocked`와 OPEN 상태 검사를 수행한다(`ProgramExecutionService.java:218-219`). 따라서 goal/intermediate/review/calculation/adjustment/feedback mutator의 공통 진입은 S3 apply와 같은 program pessimistic lock 계열을 사용한다.

`ProgramAccess.participant`가 권한 확인을 위해 participant를 먼저 읽는 구조(`ProgramAccess.java:20`)는 남아 있으므로, 최종 동시성 검증에서는 lock 획득 뒤 source 재조회·version 충돌·stale 409가 실제로 보장되는지 확인한다. 이 잔여 검증은 설계/착수 blocker가 아니라 구현 runtime QA 항목이며, 검증 전에는 source freshness를 실증 PASS로 과장하지 않는다.

## 6. Tenant·object 권한 및 개인정보

- preview/apply/history는 `HR_ADMIN`/`SUPER_ADMIN` operator만 허용한다. actor tenant의 program을 `findByIdAndTenantId`로 찾고, 모든 participant/goal/assignment/submission/employee도 같은 tenant와 해당 program에 속하는지 검증한다.
- recipient employee는 서버가 assignment 또는 participant 관계에서 파생한다. 요청에 포함된 임의 employee ID, 다른 program의 assignment, revoked/inactive employee, 다른 tenant ID는 403/404 또는 blocked로 fail-closed 한다.
- `GET /me/notifications`와 read는 현재 actor employee와 recipient ID가 일치해야 한다. operator가 전체 inbox를 열람하는 우회 endpoint를 S3에 만들지 않는다.
- notification body에는 “어떤 프로그램의 어떤 stage/action이 미완료인지”와 UI 링크용 opaque IDs만 포함하고, score, reviewer opinion, goal definition, 주민번호/연락처 등 PII는 포함하지 않는다. 감사 details도 counts/hash/reason만 보관한다.

## 7. 외부 SMTP 미연결 경계

S3 apply는 `IN_APP` persistence만 수행한다. `ProgramMailSender`, `dispatch`, EMAIL `READY`/`CONFIG_REQUIRED` 상태를 호출하거나 변환하지 않는다. 운영 환경에 SMTP가 설정되어 있어도 S3 요청으로 외부 메일이 나가서는 안 된다. 기존 generic EMAIL 기능은 별도 기능으로 남기되, S3 route/DTO에 channel 선택을 노출하지 않는다.

## 8. 성능·감사·i18n

- preview/apply 후보와 history는 hard cap(기존 suite 계약의 bounded 100 수준)과 pagination을 명시하고, SQL은 모든 조건에 `tenant_id`를 선두로 둔다. 무제한 `findAllByTenantIdAndProgramId...` 결과를 한 번에 mail/notification으로 만들지 않는다.
- apply는 service transaction에서 상태 재검증·unique insert·audit를 묶는다. 외부 호출은 없으므로 transaction 밖의 SMTP/HTTP retry는 없다. lock/unique/optimistic conflict는 `409`와 traceId로 반환한다.
- audit event는 actor, program, preview/apply kind, reason, UTC reminderOn, counts, previewHash/dedupe result, blocked reason을 기록한다. 새 event type이 필요하면 additive enum/migration으로 등록하며 기존 audit row를 수정하지 않는다. `REMINDER_PREVIEWED`, `REMINDER_APPLIED`, `REMINDER_STALE`, `REMINDER_BLOCKED` 같은 명명은 구현 계약에서 고정한다.
- FE는 server response를 그대로 표시하고 participant/reviewer 배열로 owner를 재계산하지 않는다. React Query key에 tenant/program/filter를 포함하고 apply 후 S3 query와 기존 inbox query를 invalidate한다. 신규 용어/에러 키는 `ko/en/ja/zh-CN/vi`를 준비한다. 공유 `easy-standards/glossary/i18n-terms.md` 수정은 이번 S3 범위가 아니며, 필요 시 표준 갱신 후보로만 보고한다.

## 9. 독립 인수 시나리오 (구현 후)

| ID | 검증 |
|---|---|
| C01 | tenant A actor가 tenant B program/participant/notification을 조회·apply하지 못함 (404/403) |
| C02 | non-operator preview/apply 거부; employee inbox/read는 자기 recipient만 허용 |
| C03 | GOAL author/approval/self-report, INTERMEDIATE, SELF_REVIEW, REVIEW round 1/2, CALIBRATION, FEEDBACK delivery/acknowledgement/resolution의 owner 또는 owner 없음이 정확히 매핑됨 |
| C04 | 완료 상태, excluded/deleted participant, revoked/inactive owner, disabled stage가 후보/알림에서 제외됨 |
| C05 | no owner/operator-only CALCULATION은 임의 수신자 없이 `UNASSIGNED/BLOCKED`로 남음 |
| C06 | preview 이후 submission 완료/assignment 교체/participant 제외/정책 변경 시 apply 409 stale, notification 0건 |
| C07 | source가 동일한 같은 날 재시도·동시 새 idempotency key는 `READY/ALREADY_QUEUED` 허용 및 duplicate suppression, DB row/알림함 row 1개 |
| C08 | 같은 tuple 두 동시 apply는 unique/lock으로 1개만 생성, 500/partial duplicate 없음 |
| C09 | 다른 stage/round/owner/assignment 또는 다음 UTC day는 의도된 새 episode만 생성 |
| C10 | apply는 IN_APP만 저장하고 SMTP/ProgramMailSender/dispatch 호출이 없음 |
| C11 | 알림 body/audit에 score·opinion·불필요 PII가 없고 audit counts/hash/reason/traceId가 남음 |
| C12 | bounded page/size, tenant-leading query, 409 mapping, 5 locale UI/type/build 및 기존 generic notification regression 통과 |

## 10. 게이트 상태

- **설계 게이트: PASS.** 책임자 결정·완료 판정은 현재 PA의 participant/reviewer/submission/calc/adjustment/feedback 상태로 정의 가능하며, HCM/Core Master·외부 SMTP 확장은 필요 없다.
- **구현 게이트: PASS.** Sol BE focused 17/17, full 322/322, bootJar 및 source-temp parity 84/0 mismatch가 PASS다. previewHash stale 409, server-derived owner, unique/동시성, IN_APP-only가 구현·테스트되었다.
- **S3 실행/경계 게이트: PASS (증분 범위).** 실제 API 39/39, browser 16/16, S1 regression 22/22, S2 regression 27/27, FE final source/temp 9/9 same hash, DB IN_APP READ 2/SENT 5/dedupe 0, Flyway 003 성공 근거를 반영했다. generic `recipientEmployeeIds` queue와 기존 EMAIL/dispatch는 승인된 기존 기능으로 보존하며, S3는 별도 `/incomplete-reminders:preview|:queue` DTO·service 경로에서 server-derived recipient와 IN_APP-only 저장을 사용한다.
- **운영 전송: OUT OF SCOPE.** SMTP 설정·worker·scheduler·배포·외부 발신은 이번 S3에서 검증하거나 활성화하지 않는다. 따라서 PASS는 본 증분 기능의 local Windows/API/browser 검증 범위에 한정된다.

S3 독립 QA는 완료되었으며, 운영 전송/배포는 별도 gate로 남긴다. 이 문서는 S3 증분 범위의 계약·표준·구현·실행 gate를 판정하며, 전체 제품군 backlog 완료를 의미하지 않는다.
