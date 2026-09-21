# S2 프런트 계획 — KPI 연계 (Phase 3a)

작성: 2026-09-08  
상태: 계약 초안 대기. 이 문서는 구현 지시나 새 API 계약이 아니다.

## 현재 표면과 재사용 경계

| 현재 표면 | 확인한 사실 | S2에서의 처리 |
|---|---|---|
| `frontend-vite/src/features/evaluation-programs/pages/ProgramOperationsPage.tsx` | 프로그램·참여자를 React Query로 읽고, 선택한 참가자의 세부 카드 및 운영 도구를 표시한다. S1 자동 배정 도구도 이 운영 표면에 있다. | HR 운영자가 참가자를 고른 뒤 그 참가자의 **ProgramGoal 하나 ↔ KPI assignment 하나**를 명시 연결하는 진입점 후보다. 프로그램 전체 재계산이나 KPI 원본 편집을 이 페이지에 추가하지 않는다. |
| `frontend-vite/src/features/evaluation-programs/api/programs.ts` | `ProgramSummaryResponse.asOfDate`, `ParticipantResponse`, `GoalResponse`, `GoalHistoryResponse`, `CalculationResponse`가 있다. `GoalResponse`는 catalog/department goal 연결만 지원하며 KPI assignment·actual·snapshot 식별자는 없다. | Goal upsert/goal history API에 KPI 필드를 억지로 섞지 않는다. Goal ID를 주소로 한 평가 프로그램 전용 S2 API/타입·query key를 둔다. `asOfDate`는 서버가 보유한 프로그램 값의 표시 전용 기준이다. |
| `frontend-vite/src/api/kpi.ts` | `MyKpiAssignmentResponse`는 KPI node, 유효 weight/target, latest actual/as-of, achievement rate를 제공하며 파일 주석상 파생값은 BE 계산·FE 재계산 금지다. actual은 append-only/supersede 모델이다. | S2 preview/snapshot 응답은 이 원본을 **참조**하되, 평가 프로그램용 frozen response가 계약되지 않은 한 이 타입을 캐스팅해 재사용하지 않는다. |
| `frontend-vite/src/pages/kpi/ActualHistoryModal.tsx` | assignment actual 이력을 as-of 내림차순으로 조회하고 supersede 상태를 표시한다. | 개인 KPI 화면의 편집/정정 모달은 S2에 이식하지 않는다. 필요하면 고정된 근거의 읽기 전용 행(실적일·source·superseded 여부)을 별도 wrapper에서 표시한다. |
| `frontend-vite/src/pages/review/ReviewKpiItemsTable.tsx` | read-only 표가 node/weight/target/latest actual/achievement rate/auto score/item score를 렌더링한다. score-input 모드는 manager score를 로컬 폼 상태로 받는다. | S2 응답이 `ReviewKpiItemResponse`와 정확히 호환할 때만 `readOnly` 모드를 사용한다. 그렇지 않으면 S2 read-only wrapper/table을 만든다. `score-input`은 S2 범위 밖이다. |

## 최소 사용자 흐름 제안

1. HR 운영자가 운영 페이지에서 참가자 한 명과 그 참가자의 **ProgramGoal 하나**를 명시적으로 선택한다. 프로그램의 `asOfDate`와 기존 goal target/weight/achievement level은 읽기 전용으로 보인다.
2. 운영자가 KPI assignment 하나를 선택해 **KPI 연계 미리보기**를 요청한다. 화면은 서버가 선택한 source node, captured-at target 설명, cutoff date 이하 실적, evidence, immutable revision과 누락/차단 사유를 그대로 표시한다. 프런트는 수치·점수·대체 원본을 계산하지 않는다.
3. 운영자는 preview가 제시한 근거와 revision을 확인한 뒤, 별도 확인 모달에서 이유를 입력해 명시적으로 연결을 적용한다. 적용 요청은 preview 식별자(또는 canonical hash), 같은 goal/assignment/cutoff/reason을 그대로 echo한다.
4. 적용 성공 후 React Query의 해당 program/participant/goal/KPI-link revision 키만 무효화하고, 고정된 근거·revision·연결 시각을 읽기 전용으로 표시한다. 연결을 바꾸거나 원본이 바뀌어 stale이면 서버 409/사유를 보이고 새 preview를 요구한다. 본 연결은 evidence-only이며 기존 goal score/weight/target/achievement level과 프로그램 계산을 변경하지 않는다.

이 최소안은 대량 일괄 연결, KPI 원본/actual 수정, 수동 점수 입력, 프로그램 최종점수 자동 확정을 포함하지 않는다. 기존 수동 목표·Excel·S1 평가자 배정 표면도 보존한다.

## 예상 프런트 구조 (계약 확정 후)

```text
src/features/evaluation-programs/
  api/goalKpiLinkage.ts             # S2 types, query keys, preview/apply mutations
  components/GoalKpiLinkageTools.tsx
                                    # pure visual goal/assignment preview, confirmation, frozen revision
  pages/ProgramOperationsPage.tsx   # HR-gated selected participant/goal mount only
  programI18n.ts                    # program.kpiLinkage 5-locale parity
```

응답은 React Query 캐시가 서버 상태의 SSOT이며, 컴포넌트 `useState`는 선택/모달/확인 사유처럼 화면 로컬 값에만 쓴다. Mantine core 직접 import 대신 현행 `@easy/ui-components` 래퍼를 사용한다. 새 테이블은 계약형 타입을 사용하며 `as any`·클라이언트 계산으로 호환성을 꾸미지 않는다.

## 계약·정책 미결정 (Sol/root 확인 필요)

1. **연결 범위와 cardinality**: ProgramGoal 하나당 KPI assignment 하나인지, immutable revision을 새로 만드는 재적용/교체의 허용 조건.
2. **기준·근거 고정**: cutoff date와 program `asOfDate`의 관계, cutoff 이하 latest actual 선택 규칙, superseded actual 취급, captured-at target 설명/evidence/revision/hash의 stale 조건.
3. **평가 산식 효과**: S2는 evidence-only로 기존 goal score/weight/target/achievement level과 `CalculationResponse.contributions`를 바꾸지 않는다는 root 정책을 backend contract가 보장해야 한다.
4. **실패·권한**: source/actual/score 누락 시 READY·MISSING·BLOCKED 구분과 표시 사유, HR/SUPER 권한, 개인/manager 가시성, stale/duplicate/invalid-request HTTP·error-code 계약.
5. **확인 경계**: apply 이유의 필수 여부와 감사 이벤트명. preview→명시 확인→apply가 기본안이나, backend가 불변 snapshot을 생성하는 시점은 계약에 맞춘다.

## 규모·검증 예상

- 구현 예상: 새 API/RQ 파일 120~200 LOC, visual tool 180~320 LOC, operations mount 10~25 LOC, 5 locale 키 25~45개. 기존 KPI table shape가 정확히 호환될 때만 재사용해 감소할 수 있다.
- 계약 확정 후 Windows temporary frontend copy에만 동기화하여 npm 설치 없이 typecheck, i18n/rule gates, Vite build, fixture-backed browser preview/apply/stale/missing/employee-denial을 검증한다.
