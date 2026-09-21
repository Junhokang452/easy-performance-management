# S3 경계 QA 보고서

작성일: 2026-09-08  
범위: `ProgramReminderDtos`/`ProgramReminderService`/`ProgramReminderController`/`V20260908_003__responsible_reminders.sql` ↔ `responsibleReminders.ts`/`ResponsibleReminderTools.tsx`/5 locale  
판정: **PASS — S3 증분 범위의 backend/API/FE browser 검증 완료**

## 검증한 경계

- API 경로: `POST /api/v1/evaluation-programs/{programId}/incomplete-reminders:preview|:queue`, `GET /api/v1/evaluation-programs/{programId}/incomplete-reminders`
- FE client 경로: `apiClient`의 `/api` prefix 아래 `/v1/evaluation-programs/...` 사용
- Preview / queue DTO, enum·nullable·UTC 날짜·SHA-256 shape
- operator/tenant/program/participant/owner 도출과 IDOR 차단
- 하루 1회 dedupe, idempotency replay, program lock, unique rollback
- history `Page`와 FE page/size 20 소비
- `ko/en/ja/zh-CN/vi` action/reason/status 표시 키

## PASS

### API ↔ FE shape

- JSON response DTO shape 자체는 `ReminderPreviewResponse`의 `programId`, `reminderOn`, `zoneId`, `policyVersion`, `previewHash`, `candidates`, `exclusions`, `summary`와 FE `ReminderPreview`가 일치한다 (`ProgramReminderDtos.java:51-69`, `responsibleReminders.ts:17-21`). R01 수정 후 실제 HTTP와 generated OpenAPI 3개 경로에서 wire shape가 확인되었다.
- 후보의 enum은 Jackson 문자열로 FE의 `stage`, `status`, `ownerKind` 및 nullable `ownerRole`, `reviewerAssignmentId`, `recipientEmployeeId`, `subject/body/deepLink`에 안전하게 대응한다. BLOCKED 후보의 nullable 필드도 FE 타입이 허용한다.
- queue request는 FE가 `participantIds/stages/locale/reminderOn/previewHash/candidateKeys/idempotencyKey/reason`을 보내고, 백엔드 validation이 participant/candidate 최대 100, hash 형식, locale, reason 길이·공백을 재검증한다 (`ProgramReminderDtos.java:36-49`, `ProgramReminderService.java:80-92`). recipient/channel/template를 FE가 주입하지 않는 점도 계약과 일치한다.
- `ReminderQueueResponse`의 `queued`, `duplicateSuppressed`, `rows`와 row의 `disposition`, `notificationStatus`, `registeredAt`가 FE 소비 shape와 일치한다 (`ProgramReminderDtos.java:72-79`, `responsibleReminders.ts:26-30`). S3는 IN_APP/SENT로 생성하므로 FE의 `SENT|READ` union도 이 endpoint의 실제 값 범위와 일치한다.
- controller가 raw Spring `Page<ReminderHistoryRow>`를 반환하고 FE가 `PageEnvelope<ReminderHistoryRow>`의 `content/totalPages`를 소비한다 (`ProgramReminderController.java:36-39`, `responsibleReminders.ts:32-35,46-49`). FE는 `size=20`, 백엔드는 1..100 hard cap이다 (`ProgramReminderService.java:115-118`). 실제 history page bound도 API evidence에서 PASS했다.

### tenant / object authorization

- preview/queue는 `requireOperator` 후 actor tenant의 program을 조회하고, 모든 participant·reviewer·source·employee·notification 조회가 `tenantId` 및 path program scope를 사용한다 (`ProgramReminderService.java:72-76,80-90,121-149`). participant ID가 다른 program에 속하면 batch size가 맞지 않아 전체 실패한다.
- history는 operator만 허용하고 `access.program(actor, programId)`로 path object를 확인한 뒤 tenant+program+reminder metadata만 page 조회한다 (`ProgramReminderService.java:114-118`). generic notification은 섞이지 않는다.
- recipient는 request에서 받지 않고 SELF participant 또는 정확히 하나의 active assignment에서 서버가 도출한다 (`ProgramReminderService.java:220-239`). revoked/inactive/missing/ambiguous owner는 BLOCKED이며 임의 operator fallback이 없다.

### serialization / dedupe / lifecycle

- preview hash는 normalized scope, UTC date, locale, policy, source fingerprint/due date를 포함하고 기존 notification 상태/ID는 포함하지 않는다 (`ProgramReminderService.java:141-149,245-258`). queue는 program pessimistic lock 후 replay 조회, source 재계산, hash/stale 검사를 한다 (`ProgramReminderService.java:82-110`).
- idempotency run unique `(tenant_id, program_id, idempotency_key)`, notification dedupe unique `(tenant_id, reminder_dedupe_key)`와 tenant 선두 index가 migration/entity/repository에 정합하다 (`V20260908_003__responsible_reminders.sql`, `ProgramReminderRun.java`, `ProgramNotification.java`). unique 충돌은 409로 변환하고 같은 transaction에서 재조회하지 않는다.
- reminder notification은 channel `IN_APP`, status `SENT`, source snapshot, owner/action/stage/round, reminder day를 저장하며 SMTP/dispatch 경계를 호출하지 않는다 (`ProgramReminderService.java:241-243`).
- 완료 의미도 계약과 일치한다. participant의 실제 `stageStatus=COMPLETED`는 초기 판정에서 `COMPLETED/STAGE_COMPLETED` exclusion으로 반환된다 (`ProgramReminderService.java:157-158`). 반대로 stage가 여전히 `IN_PROGRESS`인데 모든 source가 완료된 경우 `SOURCE_INCONSISTENT`로 제외하는 계약 규칙도 그대로 구현되어 있다 (`ProgramReminderService.java:186-190,197-199,208-217`).

### locale

- FE `Locale`의 5개 값과 백엔드 `LOCALE_PATTERN`이 동일하다 (`locales.ts`, `ProgramReminderDtos.java:23-24`).
- action/reason/status/read 키는 5개 locale 모두 parity가 확인되었고, Sol 실제 reason code 13종이 모두 번역되어 있다. 서버 body도 동일 locale을 사용한다 (`ProgramReminderService.java:283-288`).

## Resolved findings / scope boundaries

### R01 해소 — preview/queue colon route (FIXED/PASS)

- Sol이 controller mapping을 명시적 full suffix로 보정하고 exact-wire MockMvc regression을 추가했다. 최종 JAR 기준 full backend 322/322, bootJar PASS다.
- `_workspace/followups-20260908/s3/responsible-reminders-local.json`에서 실제 runtime `completed=true`, 39/39 cases PASS이며 preview/queue/history 경로가 모두 동작했다. generated OpenAPI에서도 3개 경로가 정상 확인되어 R01은 닫혔다.

### F-S3-01 철회 — source 완료와 stage 완료의 구분은 계약 정합 (WITHDRAWN)

- 이전 초안의 finding은 철회한다. 계약 §3은 `stageStatus=COMPLETED`일 때만 `COMPLETED` exclusion을 요구하고, stage가 `IN_PROGRESS`인데 모든 source가 완료된 경우 `SOURCE_INCONSISTENT`/알림 없음으로 명시한다.
- 실제 코드도 이 구분을 따른다: `ProgramReminderService.java:157-158`은 stage 완료를 `COMPLETED/STAGE_COMPLETED`로 반환하고, `:186-190,197-199,208-217`은 IN_PROGRESS의 source 완료를 `NOT_APPLICABLE/SOURCE_INCONSISTENT`로 fail-closed 한다.

### F-S3-02 해소 — AGREEMENT goal 비정상 상태 fail-closed (FIXED/PASS)

- `ProgramReminderService.java:188-192`에서 goal author/approval candidate가 하나도 생성되지 않으면 `NOT_APPLICABLE/SOURCE_INCONSISTENT` exclusion을 추가한다.
- `ProgramReminderServiceTest.java:222-230`의 `impossibleAgreementGoalStateIsExcludedAsSourceInconsistent`가 `GoalMode.AGREEMENT`의 `SELF_REPORTED` 상태에 대해 candidate 0건과 `SOURCE_INCONSISTENT`를 검증한다.
- Sol focused 17/17 PASS로 전달받았으며, 이 finding은 정적 코드·테스트 기준 해소로 판정한다. 실 HTTP 39/39에서도 관련 source guard가 포함된 경로가 PASS했다.

## 범위 한계 및 비차단 보류

- Sol focused 17/17, full backend 322/322, bootJar 및 source-temp parity 84/0 mismatch가 PASS다. 실제 API 39/39에서도 tenant A/B, stale 409, concurrent unique, replay, owner, page bound, 5 locale server template, generic notification 분리가 확인되었다.
- `_workspace/followups-20260908/s3/browser/result.json`은 `passed=true`, 16/16 PASS다: 5 locale × 1440/390 overflow/layout, cancel/no POST, lost-response same-key retry, same-day duplicate UI, real stale 409, recipient inbox/read, employee control absence를 포함한다. root가 ko 390/en 1440/inbox 캡처를 직접 확인했다.
- FE final source/temp 9/9 same hash(실제 schema 포함), S1 regression 22/22, S2 regression 27/27도 root 증거로 확인했다.
- DB 실측은 IN_APP `READ` 2건·`SENT` 5건, dedupe 중복 0건, Flyway `V20260908_003` 성공으로 전달되어 apply/read/dedupe 저장 경계를 보강한다.
- 외부 SMTP/worker/scheduler/deployment와 기존 generic notification의 전체 운영 회귀는 S3 승인 범위 밖이다. S3 PASS는 이번 증분의 local Windows backend/API/browser 증거에 한정한다.
- FE의 `action`, `ownerRole`, `reasonCode`, `dueState` 타입이 백엔드 enum union보다 넓은 `string`이다 (`responsibleReminders.ts:11-15,33-35`). 런타임 차단은 아니나 향후 generated schema/contract 타입 강화 후보다.

## 재개 순서

1. 현재 증거를 S3 final checkpoint에 보존한다.
2. 제품 운영 전환 시 별도 운영 gate(외부 SMTP/scheduler/deployment)는 이번 범위 밖으로 유지한다.
