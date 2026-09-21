# S3 프런트 계획 — 미완료 책임자 독려 (Phase 3a)

상태: 계약 대기, 제품 소스 변경 없음.

## 기존 재사용 표면

- `components/OperationsUtilities.tsx`의 `NotificationTools`는 운영자용 template preview → in-app queue → dispatch 흐름과 `usePreviewNotificationsMutation`/`useQueueNotificationsMutation`을 이미 사용한다. S3는 이를 대체하거나 SMTP/email을 추가하지 않는다.
- `pages/ProgramOperationsPage.tsx`는 프로그램·참가자·stage 상태를 React Query로 읽는 HR 운영 표면이다. S3의 독려 preview/apply entry point는 이 표면에 둔다.
- `pages/NotificationInboxPage.tsx`와 `useNotificationsQuery`는 수신자의 in-app 결과 표면이다. 새 페이지 없이 이 inbox에서 전달 상태를 재사용한다.
- `api/programs.ts`는 기존 generic notification API와 query invalidation 패턴을 제공한다. S3 recipient/responsible determination은 별도 S3 API·React Query key로 모델링하며, 프런트가 reviewer/manager를 추론하지 않는다.

## 최소 UI 흐름

1. HR 운영자가 프로그램·단계·기준일(계약상 필요할 때)을 정해 **미완료 책임자 미리보기**를 요청한다.
2. 서버 응답의 책임자, 미완료 항목, 중복 억제, 미배정/차단 사유를 읽기 전용 표로 표시한다. 로컬 employee/reviewer 배열에서 수신자를 파생하지 않는다.
3. 발송 가능 행 수와 template preview를 확인한 뒤 명시적 확인 모달에서 in-app notification만 queue한다. SMTP/EMAIL dispatch는 S3 범위 밖이다.
4. 적용 결과(queued, duplicate-suppressed, blocked, unassigned)를 서버 응답 그대로 표시하고 S3 query 및 existing inbox query를 invalidate한다.

## 계약 선결사항

- preview/apply/history 정확 endpoint, program/stage filters, pagination, participant/task/responsible DTO 및 actor 권한.
- server-owned responsible determination, duplicate window/idempotency key, no-responsible/blocked status·reason code, queue 이후 audit event.
- preview hash/stale 또는 snapshot version, explicit reason/template binding, self/manager visibility와 existing generic notification queue의 공존 방식.

## 예상 구조·검증

`features/evaluation-programs/api/responsibleReminders.ts`, `components/ResponsibleReminderTools.tsx`, `ProgramOperationsPage.tsx`, `programI18n.ts` 5 locale만 우선 변경한다. UI는 `@easy/ui-components` 래퍼와 React Query cache SSOT를 사용한다. 계약 확정 뒤 Windows temp 기존 deps로 typecheck/i18n/design/local-ui/Vite build를 수행하며, API fixture 준비 전 browser 실행은 하지 않는다.
