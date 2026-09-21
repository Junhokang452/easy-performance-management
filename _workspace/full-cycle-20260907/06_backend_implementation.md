# 전체 평가 엔진 백엔드 구현 기록

## 적용 범위

- 패키지: `com.easyperformance.program`
- 진입점: `/api/v1/evaluation-programs`
- 영속화: `V20260907_004__evaluation_programs.sql`, `V20260907_006__notification_dispatch_claim.sql`, `V20260907_007__revisioned_program_adjustments.sql`
- 연동: `ResourceIntegrationService`를 통해 활성 성과·역량 카탈로그, 직원에게 보이는 부서 목표, 과제 근거 스냅샷을 검증한다.
- 지원 평가 종류: `PERFORMANCE`, `COMPETENCY`. `COMBINED`, `MULTI_RATER`는 실행 계약이 없는 상태에서 생성할 수 없으며 다면평가는 `easy-mra` 경계를 유지한다.

## 구현된 업무 흐름

1. 관리자가 초안을 만들고 기본 정보, 단계, 척도, 계산·공개 정책, 그룹 조건, 차수별 가중치, 공통 항목, 부서성과 그룹, 상대평가 정원을 타입이 있는 DTO로 설정한다.
2. 기준일 인사 스냅샷에서 대상자를 생성하거나 개별·OOXML로 추가한다. 다중 소속은 행을 분리하고 활성 가중치 합이 100인 직원만 단계를 시작한다.
3. 목표 합의자, 중간점검자, 1~3차 평가자, 조정자, 최종 피드백 담당자를 배정한다. 평가 차수 슬롯은 1부터 연속이어야 하고 그룹의 가중치 계획과 일치해야 한다.
4. 운영자는 준비된 대상만 단계별로 시작한다. 미완료·담당자 누락·그룹 미매칭 대상은 변경하지 않고 제외 사유를 반환한다. 그룹별 중간점검·자기평가 사용 여부를 반영한다.
5. 목표 작성·의견·합의요청·승인/반려·수정/재요청·자기신고와 과제 근거, 중간점검 저장/완료, 자기·차수 평가 저장/완료를 처리한다. 완료된 응답은 잠긴다.
6. `CALCULATION/IN_PROGRESS` 대상만 계산한다. 차수/부서 가중치, 입력척도 환산, 평균·표준편차 보정, 상대평가 정원과 결정적 동점 순서를 적용하고 계산 revision과 산식 근거를 남긴다.
7. 조정은 계산 revision에 연결된 별도 revision으로 보존한다. 이의 정정은 새 계산 revision과 연결된 조정 revision을 만들며 이전 조정이 새 결과를 덮지 못한다.
8. 피드백 전달, 구성원 합의/이의, 담당자 또는 HR의 유지/점수정정을 처리한다. 재계산과 마감 취소는 종전 피드백을 감사 스냅샷으로 남기고 무효화한다.
9. 모든 활성 대상이 각 그룹의 마지막 사용 단계까지 완료되고 최신 FINAL 계산·연결된 조정·피드백 조건을 충족해야 마감된다. 마감과 대상별 결과 공개는 별도 명령이다.

## 읽기·보안 경계

- 프로그램 목록은 운영자에게 전체, 구성원에게 자신 또는 담당자로 배정된 OPEN 이후 평가만 제공한다.
- 전체 `ProgramResponse.configuration`은 운영자만 읽는다. 구성원 워크스페이스는 단계 일정, 입력척도의 코드·라벨, 구성원 공개정책만 담은 `MemberProgramOverview`를 사용한다.
- 리뷰 화면은 요청자의 실제 자기평가 또는 배정 차수만 허용한다. `round=999`처럼 임의 차수로 이전 응답을 넓힐 수 없다.
- 구성원 결과는 FINALIZED이며 해당 대상에게 공개된 뒤에만 보인다. `GRADE_ONLY`는 최종 유효 등급만 반환하고 점수, 환산구간, 산식, 기여도, 경고, 이전 계산·조정 이력을 제거한다.
- 조정자만 진행 중 계산 전체를 보고, 일반 평가자·점검자는 계산과 최종 피드백을 읽지 못한다.
- 평가자 배정 공개가 꺼져 있으면 구성원/담당자는 자신의 배정만 본다. 대상자·평가자 XLSX 내보내기는 운영자 전용이다.
- 다중 소속 구성원은 `/me/participants`로 참여 행을 고르고 `/me?participantId=`로 조회한다. 서버가 최신 행 하나를 임의 선택하지 않는다.
- `/participants/{id}/employee-preview`는 HR/SUPER의 읽기 전용 구성원 화면이며 권한을 사칭하지 않고 접근 감사 이벤트를 남긴다.

## 주요 API

- 설정: `POST /`, `PUT /{id}/basic`, `PUT /{id}/definition`, `POST /{id}/common-items:apply`, `POST /{id}/open`, `POST /{id}/copy`
- 명단: `participants:generate`, `participants`, `participants:import`, `participants.xlsx`, `participants/{id}/reviewers`, `reviewers:import`, `reviewers.xlsx`
- 운영: `/{id}/stages/{stage}:start`, `/participants/{id}/stage:override`, `/{id}/dashboard`
- 구성원: `/{id}/me`, `/{id}/me/participants`, 목표·중간점검·review-context·review-submission 경로
- 결과: 계산, 조정, 피드백/이의, `/{id}:finalize`, `/{id}:cancel-finalization`, `/{id}/results:publish`
- 분석: `/{id}/results`, `/results/items`, `/results/reviewers`, `/result-feedback`, `/analytics/grade-matrix`, `/analytics/pivot`, `/analytics/reviewer-tendencies`, `/me/history`
- 내보내기: `/{id}/results.xlsx`, `/analytics/pivot.xlsx`, `/analytics/pivot.svg`
- 알림: preview, queue, dispatch, 개인 알림함/read. EMAIL은 DB에 `READY`로 먼저 저장하고 원자적으로 `SENDING`을 선점한 요청만 SMTP 전송한다. 미설정은 `CONFIG_REQUIRED`이며 `SENT`로 표시하지 않는다.
- 가이드: DRAFT 운영자 업로드, 권한 조회/다운로드, MIME·magic byte·10 MiB 제한, attachment+nosniff.

## 분석 산식과 표현

- 결과·매트릭스·피벗·성향은 FINALIZED이면서 대상별 공개된 결과만 사용한다.
- 평가자 성향은 평균, 순위, 표준편차, 의견 구체성, 단어 사전 기반 긍정/중립/부정 비율, 평균 길이, 빈도 키워드를 결정적으로 계산한다.
- 계산할 표본이나 의견이 없으면 값을 `null`로 두고 `unavailableReasons`를 함께 반환한다. 외부 AI/NLP 서비스나 AI 평가라는 표기를 사용하지 않는다.
- 스프레드시트는 실제 OOXML이다. 차트 다운로드는 실제 집계값으로 만든 SVG이며 PNG 또는 Excel이라고 잘못 표기하지 않는다.

## 검증

- 순수 도메인: 단계 순서, 차수·부서 가중치 합, 항목 가중치, 상대평가 정원, 그룹 우선순위, 40/60 차수 계산.
- 설정 사전 검증: 부서성과를 켠 경우 전용 `DEPARTMENT_RESULT` 척도를 반드시 참조하고, 상대평가 배분의 모든 등급 코드는 결과척도에 존재해야 한다.
- 계산 완결성: 활성 평가자 차수 전체가 연속되고 완료됐으며 각 제출이 현재 배정과 정확히 1:1로 일치해야 계산한다. 일부 완료 건수에 맞는 다른 가중치 계획을 선택할 수 없다.
- 권한 회귀: GRADE_ONLY 전 필드 삭제, 임의 차수 거부, 마감 뒤 목표 변경 거부, DRAFT 계산 거부, 최신 계산과 불일치한 조정으로 마감 거부.
- 전달 회귀: 동일 READY 스냅샷을 본 두 dispatch 중 원자적 claim 성공 한 건만 전송.
- 2026-09-07 전체 백엔드 테스트와 `bootJar`가 통과했고, 전체 프로그램 실제 HTTP 흐름 147/147 및 마감 취소→재계산→stale 조정 거부→새 조정·피드백→재마감/재공개 revision 흐름 30/30이 통과했다. 마지막 설정·평가자 완결성 회귀 4건은 최종 루트 테스트 실행에 포함한다.

## 명시적 경계

- 다면평가 익명성·캠페인·최소 응답자 원장은 이 제품에 중복 구현하지 않고 `easy-mra`가 소유한다.
- SMTP가 설정되지 않은 환경에서는 이메일을 외부로 보내지 않는다. IN_APP 알림과 EMAIL outbox는 별도 채널이다.
- 마감 취소는 공개를 회수하고 종전 계산·조정·피드백 증거를 보존한다. 다시 공개하려면 새 계산, 필요한 새 조정·피드백, 재마감을 거쳐야 한다.
