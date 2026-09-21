# S3 미완료 책임자 독려 — PA backend 계약

작성일: 2026-09-08  
상태: Phase 3a 계약 제안(제품 코드 미변경)  
범위: `easy-performance-management`의 평가 프로그램과 기존 내부 알림함만

## 1. 결론

S3는 기존 generic notification API에 임의 recipient 목록을 넘기는 기능이 아니다. PA가 현재
program/participant/reviewer assignment/submission 상태로 실제 미완료 업무와 개인 책임자를
계산하고, operator가 명시적으로 preview한 READY 항목만 IN_APP 알림함에 등록한다.

- 외부 SMTP, EMAIL queue/dispatch, push, scheduler, HCM/Core Master 호출은 사용하지 않는다.
- 수신자를 현재 로그인 사용자, 참가자 전체, 조직장 또는 임의 HR 사용자로 대체하지 않는다.
- 한 참가자에서 현재 진행 중인 stage만 판정한다. 미래·과거 stage는 독려 대상이 아니다.
- 평가 점수, 답변, 의견, goal 본문은 preview, 알림 body, 감사 details에 넣지 않는다.
- 알림 등록은 평가 업무의 완료·제출·승인·열람을 의미하지 않는다. 기존 IN_APP 상태 모델상
  `SENT`는 알림함 DB 등록, `READ`는 수신자가 읽음 처리한 상태다.

## 2. 확인된 기존 계약

### 2.1 프로그램 상태

- Program: `DRAFT | OPEN | FINALIZED | CANCELLED`
- Participant: `ACTIVE | EXCLUDED | DELETED`
- Stage: `GOAL | INTERMEDIATE | SELF_REVIEW | REVIEW | CALCULATION | CALIBRATION | FEEDBACK`
- Stage status: `NOT_STARTED | READY | IN_PROGRESS | COMPLETED | SKIPPED | BLOCKED`
- Reviewer roles: `AGREEMENT_REVIEWER(0)`, `CHECKER(0)`, `REVIEWER(1..3)`,
  `ADJUSTER(0)`, `FINAL_FEEDBACK(0)`; `REVOKED` assignment는 owner가 아니다.

근거는 `ProgramTypes`, `ProgramRosterService.stageBlocker`, `ProgramExecutionService`이다.
프로그램 시작 API가 `IN_PROGRESS`를 만들며, S3가 알림을 생성할 수 있는 기본 상태도
`OPEN + ACTIVE + current stage IN_PROGRESS`로 한정한다.

### 2.2 기존 notification/outbox

`ProgramNotificationService.queue`는 request의 employee ID를 그대로 신뢰해 recipient로 쓰며,
IN_APP은 즉시 `SENT + sentAt`, EMAIL은 `READY` 또는 `CONFIG_REQUIRED`로 저장한다. `dispatch`는
프로그램 전체 EMAIL READY를 조회해 외부 adapter로 보낸다. 현재 `program_notification`에는
participant/stage/action/source/dedupe가 없고 unique key도 없다.

따라서 기존 `/notifications:preview|queue|dispatch`는 수정하거나 대체하지 않고 보존한다.
S3 전용 apply는 `ProgramNotification`을 IN_APP/SENT로 직접 등록하되 기존
`ProgramMailSender`와 `dispatch`를 호출하지 않는다.

## 3. 실제 책임자와 완료 판정

공통 선행 조건은 program OPEN, participant ACTIVE, 현재 stage IN_PROGRESS이다. participant의
employee 및 assignment에서 얻은 owner는 tenant-local `RmEmployee`에서 다시 조회해 ACTIVE인
경우만 수신자가 된다. 누락·비활성·복수 active slot은 BLOCKED이며 대체 수신자를 추정하지 않는다.

| 현재 stage / action | PENDING 판정 | 실제 owner | COMPLETED / BLOCKED 판정 |
|---|---|---|---|
| `GOAL / GOAL_AUTHOR` (AGREEMENT) | goal이 없거나 `DRAFT/RETURNED` goal이 하나 이상 | participant employee (`SELF`) | 모든 goal이 `AGREED`면 완료. 참가자 owner가 inactive면 blocked. |
| `GOAL / GOAL_APPROVAL` (AGREEMENT) | `AGREEMENT_REQUESTED` goal이 하나 이상 | 정확히 한 active `AGREEMENT_REVIEWER`, round 0 | assignment 부재/복수/revoked/inactive면 blocked. `DRAFT/RETURNED`의 owner를 reviewer로 추정하지 않는다. |
| `GOAL / GOAL_SELF_REPORT` (SELF_REPORT) | goal이 없거나 `SELF_REPORTED`가 아닌 goal이 하나 이상 | participant employee (`SELF`) | 모든 goal `SELF_REPORTED`면 완료. SELF_REPORT 구성에서는 INTERMEDIATE가 금지된다. |
| `INTERMEDIATE / INTERMEDIATE_CHECK` | row 없음 또는 `DRAFT` | 정확히 한 active `CHECKER`, round 0 | row `COMPLETED`면 완료. current assignment를 owner로 쓰며 revoked/부재/복수/inactive면 blocked. |
| `SELF_REVIEW / SELF_REVIEW` | SELF round 0 submission 없음 또는 `DRAFT` | participant employee (`SELF`) | SELF submission `COMPLETED`면 완료. |
| `REVIEW / REVIEW` | currentRound assignment/submission이 아직 모두 완료되지 않음 | currentRound의 정확히 한 active `REVIEWER` | assignment와 그 ID를 참조하는 submission이 모두 `COMPLETED`여야 완료. 한쪽만 완료면 `SOURCE_INCONSISTENT`; 다음 round가 시작된 경우 다음 round만 판정. |
| `CALCULATION` | operator 계산 실행이 필요 | 개인 owner 없음 | S3 지원 대상이 아니다. `OWNER_UNADDRESSABLE`로 제외하며 임의 HR recipient를 만들지 않는다. |
| `CALIBRATION / CALIBRATION` | 최신 FINAL calculation에 연결된 adjustment 없음/`DRAFT`, 또는 과거 calculation의 완료 adjustment만 존재 | 정확히 한 active `ADJUSTER`, round 0 | 최신 calculation 연결 adjustment가 `COMPLETED`면 완료. calculation 자체가 없으면 `SOURCE_MISSING`; owner 부재/복수/inactive면 blocked. |
| `FEEDBACK / FEEDBACK_DELIVERY` | feedback 없음 또는 `DRAFT` | 정확히 한 active `FINAL_FEEDBACK`, round 0 | `DELIVERED`부터 participant action으로 전환. owner 부재/복수/inactive면 blocked. |
| `FEEDBACK / FEEDBACK_ACKNOWLEDGEMENT` | feedback `DELIVERED` | participant employee (`SELF`) | `AGREED` 또는 `RESOLVED`면 완료. appeal enabled 여부와 무관하게 동의는 가능하다. |
| `FEEDBACK / FEEDBACK_RESOLUTION` | feedback `APPEALED` | 정확히 한 active `FINAL_FEEDBACK`, round 0 | 실제 `resolveFeedback`는 operator 또는 해당 `FINAL_FEEDBACK` assignee가 실행할 수 있다. assignment 부재/복수/inactive면 blocked. |

추가 상태 의미:

- `stageStatus=COMPLETED`: `COMPLETED` exclusion. 알림 없음.
- `SKIPPED`: `NOT_APPLICABLE` exclusion. 알림 없음.
- `NOT_STARTED/READY`: `STAGE_NOT_STARTED` exclusion. 시작 권한이 operator라고 해서 특정 operator를
  owner로 추정하지 않는다.
- `BLOCKED`: `STAGE_BLOCKED` candidate. 저장된 상세 blocker가 없으므로 다른 reason을 창작하지 않는다.
- 현재 stage가 요청 stage filter 밖이면 `STAGE_FILTERED_OUT`, 지원하지 않는 CALCULATION이면
  `OWNER_UNADDRESSABLE` exclusion이다.
- 모든 source가 완료인데 participant stage가 IN_PROGRESS라면 `SOURCE_INCONSISTENT`; 알림 없음.

## 4. 날짜·마감 계약

- `reminderOn`은 preview 서버가 주입된 `Clock`으로 계산한 UTC `LocalDate`이다. FE가 오늘을
  계산하거나 preview request에 날짜를 주입하지 않는다.
- apply는 preview가 반환한 `reminderOn`을 받고 현재 UTC date와 정확히 다시 비교한다. UTC 자정을
  넘겼으면 전체 요청을 `409 PROGRAM_REMINDER_STALE`로 거부하고 재-preview한다.
- `startsOn/dueDate`는 해당 `StageDefinitionInput.startsOn/endsOn`만 사용한다. null일 때 program
  시작/종료일로 가장하지 않고 null을 반환한다.
- 현재 stage가 이미 IN_PROGRESS이면 dueDate 전에도 수동 독려는 가능하다. dueDate는
  `NO_DUE_DATE | BEFORE_DUE | DUE_TODAY | OVERDUE` 설명에만 사용한다.
- dueDate와 mutable source version은 dedupe key가 아니라 preview hash에 들어간다.

## 5. 재독려·멱등 정책

제안 기본값은 **동일 logical work episode마다 UTC calendar day 1회**이다. 자동 반복은 없으며,
다음 UTC 날짜에 operator가 다시 preview/apply해야 새 알림을 등록할 수 있다.

```text
episodeKey = SHA-256(
  tenantId, programId, participantId, currentStage, action, currentRound,
  ownerEmployeeId, assignmentId-or-SELF, policyVersion
)

dedupeKey = SHA-256(episodeKey, reminderOn)
```

- `policyVersion = RESPONSIBLE_REMINDER_V1`.
- dueDate, participant/goal/submission rowVersion, draft 내용은 episode/dedupe key에 넣지 않는다.
  같은 날 마감일 표시나 draft 편집만으로 재발신되지 않는다.
- owner/assignment/round/action이 바뀌면 실제 새 업무 episode이다.
- DB unique `(tenant_id, reminder_dedupe_key)`가 최종 중복 방어선이다.
- apply의 `idempotencyKey`는 `(tenant_id, program_id, idempotency_key)` unique run으로 보존한다.
  같은 key+같은 request는 저장된 response를 그대로 반환하고, 같은 key+다른 request는
  `409 PROGRAM_REMINDER_CONFLICT`이다.

이 하루 1회/UTC 정책은 2026-09-08 사용자 승인으로 확정되었다. tenant timezone SoR가 생기기
전에는 JVM default timezone을 쓰지 않는다.

## 6. HTTP 계약

Base: `/api/v1/evaluation-programs/{programId}`. 세 endpoint 모두 HR_ADMIN/SUPER_ADMIN operator만
허용하고 path program을 actor tenant로 조회한다.

### 6.1 Preview

`POST /incomplete-reminders:preview`

Request:

```json
{
  "participantIds": ["uuid"],
  "stages": ["GOAL", "INTERMEDIATE", "SELF_REVIEW", "REVIEW", "CALIBRATION", "FEEDBACK"],
  "locale": "ko"
}
```

- `participantIds`: required, 1..100, duplicate는 422. 각 ID가 tenant/path program에 속하지 않으면
  전체 404이고 부분 정보는 반환하지 않는다.
- `stages`: nullable/empty이면 지원하는 위 6종 전체. CALCULATION 또는 중복은 422.
- `locale`: `ko | en | ja | zh-CN | vi`, required. 이는 server-owned 알림 문구 locale이며 recipient
  locale을 추정한 값이 아니다.

Response `ReminderPreviewResponse`:

```ts
type ReminderPreviewResponse = {
  programId: string;
  reminderOn: string;             // UTC yyyy-MM-dd
  zoneId: 'UTC';
  policyVersion: 'RESPONSIBLE_REMINDER_V1';
  previewHash: string;            // lowercase SHA-256; duplicate 상태/notification ID 제외
  candidates: ReminderCandidate[];
  exclusions: ReminderExclusion[];
  summary: ReminderSummary;
};

type ReminderCandidate = {
  candidateKey: string;           // episodeKey, lowercase SHA-256
  participantId: string;
  participantEmployeeId: string;
  participantName: string;
  stage: 'GOAL'|'INTERMEDIATE'|'SELF_REVIEW'|'REVIEW'|'CALIBRATION'|'FEEDBACK';
  currentRound: number;
  action: 'GOAL_AUTHOR'|'GOAL_APPROVAL'|'GOAL_SELF_REPORT'|'INTERMEDIATE_CHECK'|
          'SELF_REVIEW'|'REVIEW'|'CALIBRATION'|'FEEDBACK_DELIVERY'|
          'FEEDBACK_ACKNOWLEDGEMENT'|'FEEDBACK_RESOLUTION';
  ownerKind: 'SELF'|'ASSIGNMENT'|'UNRESOLVED';
  ownerRole: 'AGREEMENT_REVIEWER'|'CHECKER'|'REVIEWER'|'ADJUSTER'|'FINAL_FEEDBACK'|null;
  reviewerAssignmentId: string|null;
  recipientEmployeeId: string|null;
  recipientName: string|null;
  startsOn: string|null;
  dueDate: string|null;
  dueState: 'NO_DUE_DATE'|'BEFORE_DUE'|'DUE_TODAY'|'OVERDUE';
  status: 'READY'|'ALREADY_QUEUED'|'BLOCKED';
  reasonCode: string|null;
  existingNotificationId: string|null;
  subject: string|null;
  body: string|null;
  deepLink: string|null;
  sourceFingerprint: string;      // opaque SHA-256, source contents are not exposed
};

type ReminderExclusion = {
  participantId: string;
  currentStage: string|null;
  currentRound: number;
  status: 'COMPLETED'|'NOT_APPLICABLE';
  reasonCode: 'PARTICIPANT_NOT_ACTIVE'|'STAGE_NOT_STARTED'|'STAGE_COMPLETED'|
              'STAGE_SKIPPED'|'STAGE_FILTERED_OUT'|'OWNER_UNADDRESSABLE'|'SOURCE_INCONSISTENT';
};

type ReminderSummary = {
  requestedParticipants: number;
  ready: number;
  alreadyQueued: number;
  blocked: number;
  completed: number;
  notApplicable: number;
};
```

한 participant의 GOAL이 authoring과 approval을 동시에 기다리면 서로 다른 action candidate가 나올
수 있다. 후보 정렬은 `participantId, stage canonical order, currentRound, action, recipientEmployeeId`
고정이다. `previewHash`는 normalized scope, UTC reminderOn, program row/definition version, 반환 row의
원본 업무 상태·dueDate·owner employee sourceVersion, participant/assignment/goal/submission/intermediate/latest
calculation/adjustment/feedback ID·revision·rowVersion/status를 canonical JSON으로 만든 SHA-256이다.
`READY/ALREADY_QUEUED` 표시, `existingNotificationId`, notification status/readAt은 source hash에서
제외한다. 알림 등록 자체가 같은 원본 preview를 stale로 만들지 않기 때문이다.

`subject/body/deepLink`는 READY/ALREADY_QUEUED에만 존재한다. BLOCKED에는 null이다. 서버 템플릿은
5 locale 고정 문구이며 프로그램명, stage/action, participant 표시명, dueDate, 아래 상대 경로만
사용한다.

- SELF action: `/evaluations/{programId}`
- assignment action: `/admin/evaluation-programs/{programId}/participants/{participantId}/review/{round}`

치환 후 subject 1..200, body 1..8000을 서버에서 다시 검사한다. 초과는 422이고 DB 500으로 넘기지
않는다. CR/LF header injection 형태는 허용하지 않는다.

### 6.2 Apply / inbox registration

`POST /incomplete-reminders:queue`

Request `ReminderQueueRequest`:

```json
{
  "participantIds": ["uuid"],
  "stages": ["GOAL", "REVIEW"],
  "locale": "ko",
  "reminderOn": "2026-09-08",
  "previewHash": "64-char lowercase sha256",
  "candidateKeys": ["64-char lowercase sha256"],
  "idempotencyKey": "uuid",
  "reason": "마감 전 미완료 업무 독려"
}
```

- scope 필드는 preview와 정확히 같아야 한다.
- `candidateKeys`: 1..100, duplicate 422; 현재 source가 동일한 READY 또는 ALREADY_QUEUED key를
  허용한다. READY는 신규 등록, ALREADY_QUEUED는 기존 notification을 재사용한다.
- `reason`: required, trim 후 1..500.
- channel/recipient/subject/body는 request에 없다.

Response `ReminderQueueResponse`:

```ts
type ReminderQueueResponse = {
  programId: string;
  reminderOn: string;
  policyVersion: 'RESPONSIBLE_REMINDER_V1';
  previewHash: string;
  idempotencyKey: string;
  queued: number;
  duplicateSuppressed: number;
  rows: ReminderQueueRow[];
};

type ReminderQueueRow = {
  candidateKey: string;
  notificationId: string;
  participantId: string;
  recipientEmployeeId: string;
  stage: string;
  action: string;
  disposition: 'QUEUED'|'DUPLICATE_SUPPRESSED';
  notificationStatus: 'SENT'|'READ';
  registeredAt: string;
};
```

Apply 순서:

1. actor/tenant/path program 검증 후 program PESSIMISTIC_WRITE lock을 가장 먼저 획득한다.
2. 같은 idempotency key run을 lock 안에서 다시 조회한다. request hash가 같으면 저장 response를
   반환하고, 다르면 409이다. exact replay는 이미 완료된 쓰기의 조회이므로 이후 program이
   FINALIZED/CLOSED가 되었어도 저장 response를 반환한다. 신규 queue만 OPEN을 요구한다.
3. participant/source/owner를 DB에서 batch 재조회하고 preview를 동일 Clock/locale로 재계산한다.
4. reminderOn과 source-only previewHash가 다르거나 선택 key의 실제 source/owner가 바뀌면 전체
   409 stale이다. 동일 source의 READY/ALREADY_QUEUED 전환은 stale이 아니며 기존 notification을
   `DUPLICATE_SUPPRESSED`로 반환한다. 일부만 알림 등록하지 않는다.
5. run placeholder를 `saveAndFlush`, 각 notification을 IN_APP/SENT로 `saveAndFlush`, response JSON을
   run에 저장하고 `REMINDERS_QUEUED` audit event를 같은 transaction에 기록한다.
6. 동일 tuple/day unique 충돌은 `PROGRAM_REMINDER_CONFLICT` 409로 rollback한다. PostgreSQL aborted
   transaction 안에서 재조회하지 않는다. 정상 동일 요청은 program lock 후 run 재조회가 처리한다.

Preview는 read-only이며 domain/audit row를 만들지 않는다. 성공 apply audit details에는 reason,
reminderOn, policyVersion, previewHash, queued/duplicate count, candidate key만 저장하고 메시지/PII/
source JSON은 넣지 않는다. 실패는 기존 trace/error 관측을 사용하며 rollback된 TX에 audit을 억지로
남기지 않는다.

### 6.3 Operator history

`GET /incomplete-reminders?page=0&size=20`

- size 1..100, createdAt DESC.
- `Page<ReminderHistoryRow>`이며 reminder metadata가 있는 S3 notification만 반환한다. generic
  notification은 섞지 않는다.
- row: `notificationId, participantId, recipientEmployeeId, stage, currentRound, action, reminderOn,
  policyVersion, notificationStatus, subject, body, deepLink, registeredAt, readAt, idempotencyKey`.
- employee 개인 열람은 기존 `GET /api/v1/evaluation-programs/me/notifications`와 tenant+recipient
  read endpoint를 그대로 사용한다. operator가 타인의 전체 inbox를 보는 새 endpoint는 만들지 않는다.

## 7. 동시성 경계

`ProgramRosterService.requireOpen`, S2 KPI apply, REVIEWER complete와 S3 queue는 동일 program lock을
사용한다. S3 구현에서는 `ProgramExecutionService`의 변경 진입점도 동일 lock helper를 사용하도록
통일하여 GOAL/INTERMEDIATE/CALIBRATION/FEEDBACK 완료와 queue를 직렬화한다.

구현 시 `ProgramExecutionService`의 mutation 전용 `requireOpen`을 `findLocked` 기반으로 통일한다.
이 private helper를 호출하는 create/update/request/decide/self-report goal, intermediate, review draft/
complete, calculate, adjustment, feedback deliver/agree/appeal/resolve가 같은 program lock을 획득한다.
read-only 조회에는 lock을 추가하지 않는다. S3 apply도 lock 후에 source/context를 처음 조회한다.

이 순서로 source writer가 먼저 lock/commit하면 apply 재조회가 완료를 보고 stale 처리하고, apply가
먼저 lock하면 알림 등록이 source 완료보다 먼저 직렬화된 정상 순서가 된다. `ProgramNotification`
unique는 lock 누락/다중 프로세스에 대한 마지막 방어선이다.

## 8. 저장 모델과 repository 계약

Additive migration `V20260908_003__responsible_reminders.sql` 제안:

1. `program_reminder_run`
   - id UUIDv7, tenant_id, program_id, idempotency_key UUID, request_hash VARCHAR(64), preview_hash VARCHAR(64),
     reminder_on DATE, policy_version, locale, reason, actor_employee_id, response_json JSONB, audit columns.
   - unique `(tenant_id, program_id, idempotency_key)`; index `(tenant_id, program_id, created_at DESC)`.
2. `program_notification` nullable additive columns
   - reminder_run_id, participant_id, reminder_episode_key VARCHAR(64), reminder_dedupe_key VARCHAR(64),
     reminder_on DATE, reminder_policy_version, reminder_stage, reminder_action, reminder_round,
     deep_link VARCHAR(500), source_snapshot_json JSONB.
   - partial unique `(tenant_id, reminder_dedupe_key) WHERE reminder_dedupe_key IS NOT NULL`.
   - history index `(tenant_id, program_id, reminder_on DESC, created_at DESC)`.

모든 인덱스는 tenant_id 선두다. 기존 notification row는 새 컬럼 null로 유지되어 generic 계약과
checksum을 보존한다. 기존 적용 migration은 수정하지 않는다.

Batch scope를 지키고 N+1을 만들지 않도록 tenant+program+participantIds/ID IN finder를 participant,
reviewer, goal, submission, intermediate, calculation, adjustment, feedback, employee repository에
additive로 둔다. optional date null JPQL을 쓰지 않고 날짜 판정은 이미 bounded인 source rows에서 한다.

## 9. 오류와 권한

- 기존 `PROGRAM_NOT_FOUND/PARTICIPANT_NOT_FOUND/PROGRAM_FORBIDDEN/PROGRAM_INVALID`을 재사용한다.
- 신규 `PROGRAM_REMINDER_STALE` = 409, `PROGRAM_REMINDER_CONFLICT` = 409.
- program이 OPEN이 아니면 locked/stage error, 다른 tenant/program participant는 404, non-operator는
  403, duplicate IDs/unsupported stage/locale/template result 길이/선택 불가 key는 422이다.
- blocked/unassigned/inactive owner는 preview의 명시 row이며 API 500이 아니다.
- request에는 recipient employee ID가 없으므로 IDOR 주입 표면을 만들지 않는다.

## 10. 예상 변경 파일

제품 구현 승인 후 PA backend 소유 범위:

- 신규: `ProgramReminderRun`, `ProgramReminderService`, 필요시 reminder DTO/template 전용 클래스,
  run repository, `V20260908_003__responsible_reminders.sql`, 집중 테스트.
- 수정: `ProgramNotification`/repository, `EvaluationProgramController`, `ProgramDtos`, `ProgramTypes`,
  `ProgramErrorCode`, `ProgramExecutionService` private lock helper, batch finder가 필요한 program/readmodel
  repositories, generic notification 회귀 테스트.
- 비변경: HCM, easy-platform-core, SMTP 설정/adapter, generic notification endpoint, S1/S2 계약,
  frontend, 외부 DB/배포.

예상 14~18 source/test/migration 파일이다. repository batch finder를 생략해 파일 수를 줄이는 대신
100명 × 단계별 N+1을 만드는 방식은 허용하지 않는다.

## 11. 구현 후 필수 검증

1. 각 action owner와 완료/blocked/unassigned/inactive/ambiguous 판정 단위 테스트.
2. 합의 GOAL의 DRAFT/RETURNED→SELF, AGREEMENT_REQUESTED→AGREEMENT_REVIEWER 분리.
3. REVIEW current round 및 assignment/submission 양쪽 완료 정합.
4. FEEDBACK DRAFT→FINAL_FEEDBACK, DELIVERED→SELF, APPEALED→FINAL_FEEDBACK.
5. preview 무변경, stale 전체 rollback, 같은 idempotency exact replay, 같은 tuple/day·동시 apply 1행.
6. 다음 UTC day 새 알림 허용, JVM default timezone 비의존.
7. IN_APP/SENT만 생성, `ProgramMailSender`/dispatch 호출 0회, 개인 inbox/read 경계 유지.
8. 치환 후 subject/body 길이 검증, score/opinion/body의 audit 비노출.
9. tenant/cross-program/non-operator/unknown object 경계와 bounded 100/page 100.
10. focused → 전체 backend test → bootJar, root 실제 PostgreSQL/HTTP, Terra OpenAPI/type/build/browser,
    Luna 독립 C01~C12 QA.

## 12. Phase 3a 결정 상태

- 확정 제안: server-derived owner, current IN_PROGRESS stage only, IN_APP only, preview→explicit apply,
  fail-closed owner/source, program-lock serialization, DB unique, server templates 5 locale, UTC date.
- 사용자 승인 완료: `RESPONSIBLE_REMINDER_V1`의 재독려 빈도는 logical episode당 UTC day 1회로
  고정한다. tenant timezone SoR가 생기기 전에는 tenant-local day를 추론하지 않는다.
- 위 정책 외에 제품 의미를 바꾸는 결정은 필요 없다. Phase 3a gate 이후에만 제품 코드를 수정한다.
