> 작성 시각: 2026-06-11 / 작성자: Claude (easy-suite-orchestrator 산출물) / 목적: 도메인 모델 ERD 16 entity + 페르소나 5 × 화면 30 매트릭스 + 사이클 시퀀스 박제

# 03. Domain Model + Screen Catalog + Cycle Flow

---

## Part A — 도메인 모델 (ERD 텍스트)

### A.1 신규 entity 16건 (제안)

#### Group 1: 사이클·정책 (P0)

##### Entity 1: `EvaluationCycle`
- **본질**: 평가 사이클(분기 / 반기 / 연간) 단일 SoT — 회사·기간·정책·진행 단계
- **상태기계**: `PLANNED → ACTIVE → GOAL_SETTING → MID_REVIEW → SELF_REVIEW → MANAGER_REVIEW → CALIBRATION → FINALIZED → APPEAL_OPEN → CLOSED → ARCHIVED`
- **주요 필드**: `id, code(예 "Y2026-H1"), name, type(QUARTER|HALF|ANNUAL|CUSTOM), startDate, endDate, currentStage, policyId, createdBy, createdAt`
- **인덱스**: `(tenant_id, code) UNIQUE, (tenant_id, currentStage), (tenant_id, type, startDate)`
- **권한**: HR 어드민만 생성·전이

##### Entity 2: `EvaluationPolicy`
- **본질**: 사이클별 정책 — 어떤 방법론을 활성, 가중치, 강제 분포, appeal 활성 여부
- **append-only**: 사이클 시작 후 변경 불가 (immutable snapshot)
- **주요 필드**: `id, cycleId, methods(JSONB — [{type:KPI,weight:0.6,mandatory:true},{type:MBO,weight:0.3},{type:COMPETENCY,weight:0.1}]), stages(배열), ratingDistribution(JSONB — {mode:FORCED|ABSOLUTE|HYBRID, distribution:{S:0.1,A:0.2,B:0.4,C:0.2,D:0.1}}), appealEnabled, appealWindowDays, selfReportedEnabled, bscEnabled`
- **S2S**: 없음 (내부 SoR)

#### Group 2: KPI / BSC (P0)

##### Entity 3: `KpiTree`
- **본질**: 조직 단위 KPI 트리 (전사 → 본부 → 팀 → 개인 cascade)
- **주요 필드**: `id, cycleId, ownerOrgUnitId(FK rm_org_unit), name, level(CORPORATE|DIVISION|TEAM|INDIVIDUAL), bscEnabled`
- **인덱스**: `(tenant_id, cycle_id, owner_org_unit_id)`

##### Entity 4: `KpiNode`
- **본질**: 개별 KPI 지표 — 가중치·목표·측정단위·BSC 관점·자동/수동
- **주요 필드**: `id, treeId, parentId, label, weight(decimal 0~1), target(decimal), unit(예 "건", "%", "원", "점"), bscPerspective(FINANCIAL|CUSTOMER|INTERNAL_PROCESS|LEARNING_GROWTH|NONE), source(MANUAL|AUTO), sourceConfig(JSONB — 자동 수집 시 endpoint/query), cascadeFromId(부모 KPI 참조)`
- **무결성**: `parentId 가 있으면 같은 KpiTree 안에서만, 형제 weight 합 = 1.0` (service-level 검증)
- **인덱스**: `(tenant_id, tree_id, parent_id)`

##### Entity 5: `KpiAssignment`
- **본질**: KPI 노드를 조직 단위 → 개인에게 cascade 매핑
- **주요 필드**: `id, kpiNodeId, employeeId(FK rm_employee), weight(개인 가중치 override 가능), targetOverride`
- **인덱스**: `(tenant_id, kpi_node_id, employee_id) UNIQUE`

##### Entity 6: `KpiActual`
- **본질**: 시점별 실적 (수동 입력 또는 자동 수집)
- **주요 필드**: `id, kpiAssignmentId, asOfDate, actualValue, source(MANUAL|AUTO|IMPORT), reportedBy, evidenceUrl, comment`
- **append-only**: 정정은 신규 row + supersedes 컬럼 (talent ReviewDecision 패턴 동형)
- **인덱스**: `(tenant_id, kpi_assignment_id, as_of_date DESC)`

#### Group 3: MBO / OKR (P0 + P1)

##### Entity 7: `Mbo`
- **본질**: 사이클 내 사원의 MBO 합의 목표 묶음
- **상태기계**: `DRAFT → SUBMITTED → APPROVED → IN_PROGRESS → REVIEWED → FINALIZED`
- **주요 필드**: `id, cycleId, employeeId, status, totalWeight(검증 = 1.0)`
- **인덱스**: `(tenant_id, cycle_id, employee_id) UNIQUE`

##### Entity 8: `MboItem`
- **본질**: 개별 MBO 항목 — 정성·정량 혼합 가능
- **주요 필드**: `id, mboId, label, description, weight, target, actualText, score(매니저 평가 후), comments(JSONB — self/manager/peer)`
- **무결성**: `Σ weight = 1.0` (service-level)

##### Entity 9: `Okr` (기존 PersonalOkr 진화)
- **본질**: Objective 컨테이너 — 회사 / 본부 / 팀 / 개인 cascade
- **주요 필드**: `id, cycleId, ownerType(CORPORATE|DIVISION|TEAM|INDIVIDUAL), ownerId(orgUnitId 또는 employeeId), title, parentOkrId(alignment), status(DRAFT|ACTIVE|AT_RISK|ON_TRACK|ACHIEVED|MISSED|ARCHIVED), confidence(0~10)`
- **인덱스**: `(tenant_id, cycle_id, owner_type, owner_id), (tenant_id, parent_okr_id)`

##### Entity 10: `Objective`
- **본질**: Okr 의 질적 목표 텍스트 — Lattice 모델 정합 시 Okr 와 통합 가능, 분리하면 다중 O 지원
- **주요 필드**: `id, okrId, statement, rationale, progress(0~1, 자동 계산 = KR 평균)`

##### Entity 11: `KeyResult`
- **본질**: Objective 의 정량 측정 지표 3~5개
- **주요 필드**: `id, objectiveId, statement, startValue, targetValue, currentValue, unit, score(0~1 자동 계산 = (current-start)/(target-start)), confidence`
- **append-only**: 정정은 supersedes
- **인덱스**: `(tenant_id, objective_id)`

#### Group 4: Check-in / 역량 / 피드백 (P1)

##### Entity 12: `CheckIn` (기존 ReflectionJournal + MentorFeedback 흡수)
- **본질**: 주간·격주 매니저-사원 1:1 메모 + OKR 진척 + blocker + next action
- **주요 필드**: `id, employeeId, managerId, weekStartDate, priorities(JSONB), blockers, accomplishments, sentiment(1~5), managerNote, mode(WEEKLY_CHECKIN|MENTOR_1ON1|REFLECTION_KPT|REFLECTION_4LS|REFLECTION_SSC), okrSnapshot(JSONB)`
- **인덱스**: `(tenant_id, employee_id, week_start_date DESC)`

##### Entity 13: `Competency` / `CompetencyFramework`
- **본질**: 역량 카탈로그 — easy-job-management.SkillSet/Skill 카탈로그 수신 후 binding
- **주요 필드 (CompetencyFramework)**: `id, name, layer(CORE_VALUE|JOB|LEADERSHIP), skillSetRefId(easy-job-management read-model), targetRole`
- **주요 필드 (Competency)**: `id, frameworkId, code, label, level(JUNIOR|MID|SENIOR), bars(JSONB — behavioral indicator 5단계)`

##### Entity 14: `CompetencyAssessment`
- **본질**: 사원별 역량 평가 결과
- **주요 필드**: `id, cycleId, employeeId, competencyId, raterType(SELF|MANAGER|PEER), score(1~5), comment, evidenceLinks`
- **인덱스**: `(tenant_id, cycle_id, employee_id, competency_id, rater_type)`

#### Group 5: 종합 평가 + 등급 + Calibration (P0)

##### Entity 15: `PerformanceReview`
- **본질**: 사이클별 종합 평가 — selfEvaluation + managerReview + 360 결과 + KPI 실적 + 역량 점수 → 최종 등급
- **상태기계**: `DRAFT → SELF_PENDING → SELF_SUBMITTED → MANAGER_PENDING → MANAGER_SUBMITTED → CALIBRATION → FINALIZED → APPEAL_REQUESTED → APPEAL_RESOLVED → ARCHIVED`
- **주요 필드**: `id, cycleId, employeeId, kpiScore(0~100), mboScore, competencyScore, mraScore(mra 수신), finalScore, finalGrade(S|A|B|C|D), selfComment, managerComment, finalizedAt, finalizedBy`
- **인덱스**: `(tenant_id, cycle_id, employee_id) UNIQUE`

##### Entity 16: `CalibrationSession`
- **본질**: 부서장 합의 회의 — 매니저 점수 → 분포 조정 → 최종 등급
- **상태기계**: `PLANNED → IN_SESSION → ADJUSTED → CONFIRMED → CLOSED`
- **주요 필드**: `id, cycleId, ownerOrgUnitId, scheduledAt, participantIds(배열), adjustmentLog(JSONB — 매니저별 조정 이력), confirmedAt, confirmedBy`
- **권한**: HR 어드민 + 부서장
- **인덱스**: `(tenant_id, cycle_id, owner_org_unit_id)`

##### Entity 17: `RatingDistribution`
- **본질**: 사이클별 등급 분포 정책 + 실제 분포 결과
- **주요 필드**: `id, cycleId, orgUnitId(NULL = 전사), policyDistribution(JSONB — {S:0.1,...}), actualDistribution(JSONB), forcedApplied(boolean), simulationLog(JSONB)`

##### Entity 18: `PerformanceReport`
- **본질**: 사이클 종료 후 사원에게 공개되는 최종 리포트 — append-only
- **주요 필드**: `id, reviewId, employeeId, publishedAt, content(JSONB — final grade + KPI 결과 + Competency + 360 집계 + 매니저 comment + next action), viewedAt, acknowledged(boolean)`
- **append-only**

#### Group 6: 부가 (P2)

##### Entity 19: `Recognition`
- **본질**: 동료 칭찬 — Lattice Praise / 15Five HighFives 패턴
- **주요 필드**: `id, fromEmployeeId, toEmployeeId, message, valueTag(JSONB — 핵심가치 tag), publicLevel(PUBLIC|TEAM|PRIVATE), createdAt`

##### Entity 20: `Feedback`
- **본질**: 동료/매니저 즉시 피드백 (CFR 의 F)
- **주요 필드**: `id, fromEmployeeId, toEmployeeId, content, category(POSITIVE|CONSTRUCTIVE|COACHING), isPrivate, createdAt`

##### Entity 21: `Appeal`
- **본질**: 평가 결과 이의신청 (한국 시장 필수)
- **상태기계**: `SUBMITTED → REVIEW → DECISION(ACCEPTED|REJECTED|PARTIAL) → CLOSED`
- **주요 필드**: `id, reviewId, submittedBy, submittedAt, reason, evidenceLinks, reviewedBy, decision, decisionComment, decidedAt`
- **권한**: 사원 SUBMITTED + HR 어드민 REVIEW/DECISION

##### Entity 22: `CareerDevelopmentPlan` (CDP)
- **본질**: 사원-매니저 합의 성장 계획
- **주요 필드**: `id, employeeId, cycleId, currentLevel, targetLevel, milestones(JSONB), reviewDate`

##### Entity 23: `EngagementPulse`
- **본질**: 짧은 설문 — eNPS 등 (mra 위임 검토 가능)
- **주요 필드**: `id, cycleId, questions(JSONB), responseAggregate(JSONB — 익명 집계만), launchedAt`

##### Entity 24: `AchievementLog` (사후 성과신고)
- **본질**: 사원 연중 자유 등록 → 매니저 confirm → 사이클 결산 자동 합산
- **주요 필드**: `id, employeeId, occurredAt, title, description, evidenceLinks, category, confirmedBy, confirmedAt, score(매니저 부여), associatedCycleId`

#### Group 7: Read-Model (S2S 수신, P0)

| 테이블 | 출처 | 용도 |
|--------|------|------|
| `rm_employee` | hcm | 사원 정보(이름·email·orgUnitId·managerId·position·grade) |
| `rm_org_unit` | hcm | 조직 트리(회사→본부→부서→팀) |
| `rm_assignment` | hcm | 발령 — KPI cascade 시 사용 |
| `rm_skill_set` / `rm_skill` | easy-job-management | 역량 카탈로그 — CompetencyFramework binding |
| `rm_mra_result` | mra | 익명 360 집계만 |

→ talent `STAGE15` 패턴 동형 (source_version+synced_at, ReadModelSyncService 단일 쓰기).

---

### A.2 기존 4 도메인 흡수·보강·재사용 매핑

| 기존 entity | 새 entity 흡수 | 처리 |
|-------------|---------------|------|
| `SelfEvaluation` | `PerformanceReview` 의 self section + `selfComment` | DB 보존 + service-level view (legacy fallback 1 sprint) |
| `PersonalOkr` | `Okr(ownerType=INDIVIDUAL)` + `Objective` + `KeyResult` 로 재구성 | 마이그 V2: PersonalOkr → Okr+Objective+KeyResult 분해 (idempotent) |
| `ReflectionJournal` | `CheckIn(mode=REFLECTION_*)` 로 통합 | View + service 매핑, DB 보존 |
| `MentorFeedback` | `CheckIn(mode=MENTOR_1ON1)` + `Feedback` | View + service 매핑, DB 보존 |

→ **DB drop 없음**, 모두 view + service-level mapping 또는 idempotent 분해 마이그.

---

### A.3 KPI cascade SQL 인덱스 + 가중치 검증

```sql
-- KpiNode cascade 쿼리 최적화
CREATE INDEX ix_kpi_node_tenant_tree_parent
  ON kpi_node(tenant_id, tree_id, parent_id);

-- 가중치 합 검증 — service-level (Hibernate @PrePersist + Repository sum 쿼리)
@PrePersist
void validateWeightSum() {
  BigDecimal sum = repo.sumWeightByParent(tenantId, parentId);
  if (!sum.equals(BigDecimal.ONE)) throw new ApiException(E98xxxxx, "KPI weight sum must = 1.0");
}
```

또는 DB trigger 로 동일 검증 (Flyway V2 에 부착) — 권고: **service-level** (테스트 친화 + lib 정합).

---

### A.4 자매품 경계 — talent 송신 매트릭스

| performance event | talent 수신 endpoint | payload |
|-------------------|---------------------|---------|
| `PerformanceReview.FINALIZED` | `/api/internal/sync/performance-results` | `{employeeId, cycleId, finalScore, finalGrade, kpiScore, mboScore, competencyScore, sourceVersion}` |
| `CompetencyAssessment` 누적 | `/api/internal/sync/performance-results` (subset) | competency score breakdown |

→ talent `dbdcf96` / `STAGE_FULL` 동형 — Bearer + HMAC + sourceVersion idempotency + 미설정 503.

---

## Part B — 화면 카탈로그 (페르소나 × 화면 매트릭스)

### B.1 페르소나 5종

| ID | 페르소나 | 책임 범위 |
|----|----------|----------|
| **P_EMP** | 사원 (Employee) | 자기평가, OKR/MBO 입력, KPI 실적 등록, Check-in 응답, 본인 결과 조회, Appeal 제출 |
| **P_MGR** | 매니저 (Manager, 팀장) | 팀원 평가, Check-in 응답, 360 수집, OKR alignment |
| **P_DIR** | 본부장·임원 (Director) | 부서 KPI 트리 관리, Calibration 회의 참여, 분포 조정 |
| **P_HR** | HR 운영자 (HR Operator) | 사이클 개설·정책 정의·Calibration 운영·Appeal 검토·등급 분포 관리·리포트 발행 |
| **P_SYS** | 시스템 관리자 (Sys Admin) | 권한·설정·모니터링·감사 |

---

### B.2 화면 카탈로그 30종

#### B.2.1 사원 (P_EMP) 중심 화면

| # | 경로 | 페르소나 | 목적 | 주요 영역 | 인터랙션 | 데이터 출처 | P |
|---|------|----------|------|-----------|----------|-------------|---|
| 1 | `/my/dashboard` | P_EMP | 본인 사이클 진척 + KPI/OKR 카드 + Check-in 알림 | 사이클 stepper / 본인 KPI 카드 / 본인 OKR 카드 / 다가오는 알림 | 사이클 단계별 액션 버튼 / KPI 카드 클릭 → 실적 입력 폼 | EvaluationCycle, KpiAssignment, Okr, CheckIn | P0 |
| 2 | `/my/kpi/{cycleId}` | P_EMP | 본인 KPI 트리 + 실적 입력 | KPI 트리(부모-자식) / 가중치·목표·실적 컬럼 / 실적 등록 버튼 / 자동수집 KPI 카드 | 실적 입력 모달 / source=AUTO 는 read-only | KpiAssignment, KpiActual | P0 |
| 3 | `/my/okr/{cycleId}` | P_EMP | 본인 OKR + KR 진척 업데이트 | Objective 카드 / KR 진척 슬라이더 / Confidence | KR 클릭 → 사이드 패널(편집·comment) | Okr, KeyResult | P1 |
| 4 | `/my/self-review/{cycleId}` | P_EMP | 자기평가 폼 | KPI 자체평가 / MBO 답변 / Competency self / 코멘트 | Stepper → SUBMIT 후 lock | PerformanceReview, MboItem, CompetencyAssessment | P0 |
| 5 | `/my/checkins` | P_EMP | 주간 Check-in 스트림 | 시간순 카드 / 새 Check-in 폼 | 주간 알림 → 폼 자동 사전 채움 | CheckIn | P1 |
| 6 | `/my/feedback-inbox` | P_EMP | 받은 피드백·인정 | 피드백 카드 / 칭찬 카드 / value tag 필터 | 비공개/공개 토글 | Feedback, Recognition | P2 |
| 7 | `/my/report/{cycleId}` | P_EMP | 본인 최종 리포트 | 최종 등급 + 점수 분해 + 매니저 comment + next action | 확인(acknowledged) 버튼 / Appeal 신청 링크 | PerformanceReview, PerformanceReport | P0 |
| 8 | `/my/appeal/new` | P_EMP | 이의신청 작성 | 사유 textarea / 증빙 업로드 / 단계 안내 | 제출 → SUBMITTED 상태 | Appeal | P2 |
| 9 | `/my/career-plan` | P_EMP | 본인 성장 계획 | 현 단계·목표 단계 GAP / 마일스톤 / 추천 교육 | 매니저 1:1 준비 | CareerDevelopmentPlan, CompetencyFramework | P2 |
| 10 | `/my/achievements` | P_EMP | 사후 성과신고 | 등록 폼 / 시간순 로그 / 매니저 confirm 상태 | 자유 등록 → 매니저 confirm 대기 | AchievementLog | P1 |

#### B.2.2 매니저 (P_MGR) 중심 화면

| # | 경로 | 페르소나 | 목적 | 주요 영역 | 인터랙션 | 데이터 출처 | P |
|---|------|----------|------|-----------|----------|-------------|---|
| 11 | `/manager/team-dashboard` | P_MGR | 팀원 진척 한눈 | 팀원 행 × KPI/OKR/Review 단계 컬럼 | 행 클릭 → 사원 상세 view | rm_employee, PerformanceReview, Okr | P0 |
| 12 | `/manager/review/{cycleId}/employee/{empId}` | P_MGR | 팀원 평가 폼 | self ↔ manager 양분 + KPI 실적 표 + Competency + 360 집계 | 점수 입력 → 자동 가중 합산 | PerformanceReview, KpiActual, CompetencyAssessment, rm_mra_result | P0 |
| 13 | `/manager/kpi-tree/{cycleId}` | P_MGR | 팀 KPI 트리 + cascade | 트리(팀 → 팀원) / weight 합 검증 / 가중치 편집 | 노드 드래그·드롭 (cascade 재배치) | KpiTree, KpiNode, KpiAssignment | P0 |
| 14 | `/manager/okr/{cycleId}` | P_MGR | 팀 OKR + alignment | Objective 트리 / alignment 화살표 / 팀원 OKR 노드 | alignment 추가 | Okr, Objective | P1 |
| 15 | `/manager/checkins` | P_MGR | 팀원 Check-in 응답 | 팀원별 카드 / 응답 의무 표시 | 답글·1:1 모드 | CheckIn | P1 |
| 16 | `/manager/talent-profile/{empId}` | P_MGR | 팀원 단일 페이지 (Talent Profile) | Goals + Reviews + Competency + Career + Feedback history | tab 또는 long scroll | composite (위 전부) | P1 |
| 17 | `/manager/recognition` | P_MGR | 팀 인정 wall | 받은·보낸 칭찬 카드 / 새 칭찬 작성 | 슬랙 연동 알림 | Recognition | P2 |

#### B.2.3 본부장·임원 (P_DIR) 중심 화면

| # | 경로 | 페르소나 | 목적 | 주요 영역 | 인터랙션 | 데이터 출처 | P |
|---|------|----------|------|-----------|----------|-------------|---|
| 18 | `/director/kpi-tree/{cycleId}` | P_DIR | 본부 KPI 트리 (전체 cascade) | BSC 4 관점 컬럼 (옵션) / 전사→본부→팀 트리 / 부서별 진척 | BSC ON/OFF 토글 | KpiTree, KpiNode | P0 |
| 19 | `/director/calibration/{cycleId}` | P_DIR | 본부 Calibration grid | 매니저 점수 분포 차트 / 분포 시뮬레이션 / 조정 모드 | 드래그로 등급 이동 / 시뮬레이션 모드 ON | CalibrationSession, RatingDistribution, PerformanceReview | P0 |
| 20 | `/director/dashboard` | P_DIR | 본부 종합 dashboard | 등급 분포 / KPI 달성률 / OKR 진척 / 360 평균 | 부서 필터 | composite | P1 |

#### B.2.4 HR 운영자 (P_HR) 중심 화면

| # | 경로 | 페르소나 | 목적 | 주요 영역 | 인터랙션 | 데이터 출처 | P |
|---|------|----------|------|-----------|----------|-------------|---|
| 21 | `/hr/cycles` | P_HR | 사이클 목록·생성 | 사이클 카드 그리드 / 신규 사이클 모달 | 모달 6 옵션 (사이클 type, 정책, 강제분포, appeal, 사후신고, BSC) | EvaluationCycle, EvaluationPolicy | P0 |
| 22 | `/hr/cycle/{cycleId}/workspace` | P_HR | 사이클 운영 워크스페이스 | Stepper 단계 / 단계별 통계(제출률·평균점수·이상치) / 단계 전이 버튼 | 단계 전이 (PLANNED→ACTIVE 등) — 의무 검증 | EvaluationCycle, 통계 | P0 |
| 23 | `/hr/policy/{cycleId}` | P_HR | 정책 편집 (사이클 시작 전만) | 방법론 가중치 / 단계 정의 / 분포 정책 / appeal 옵션 | 사이클 시작 후 read-only | EvaluationPolicy | P0 |
| 24 | `/hr/distribution/{cycleId}` | P_HR | 등급 분포 시뮬레이터 (전사) | 현재 분포 vs 목표 분포 / 부서별 분포 / 강제 적용 버튼 | 시뮬레이션 후 적용 (lock) | RatingDistribution, PerformanceReview | P0 |
| 25 | `/hr/appeals` | P_HR | 이의신청 검토 큐 | 신청 카드 / 검토 폼 / 결정 옵션 | 결정 후 audit 기록 | Appeal | P2 |
| 26 | `/hr/reports/{cycleId}` | P_HR | 사이클 결산 리포트 발행 | 사원 결과 표 / 리포트 일괄 발행 버튼 / 권한 분기 노출 | 일괄 발행 → PerformanceReport append | PerformanceReport | P0 |
| 27 | `/hr/competency-framework` | P_HR | 역량 카탈로그 관리 | Framework 트리 / Competency × Level × BARS 편집 | easy-job rm_skill_set sync | CompetencyFramework, Competency | P1 |
| 28 | `/hr/calibration-sessions` | P_HR | 부서별 Calibration 세션 관리 | 일정 / 참가자 / 상태 | 세션 생성·참가자 초대 | CalibrationSession | P0 |

#### B.2.5 시스템 관리자 (P_SYS) 화면

| # | 경로 | 페르소나 | 목적 | 주요 영역 | 데이터 출처 | P |
|---|------|----------|------|-----------|-------------|---|
| 29 | `/admin/audit-log` | P_SYS | 평가 변경 audit | 사원·사이클·entity 필터 + 변경 이력 | (BE-CC-4 audit chain) | P1 |
| 30 | `/admin/s2s-config` | P_SYS | S2S 채널 모니터링 (hcm/mra/talent/easy-job) | 채널 상태 / sourceVersion 진척 / 실패 큐 | TenantSecret config | P1 |

---

### B.3 핵심 화면 ASCII 와이어프레임

#### Wireframe 1: HR `/hr/cycle/{cycleId}/workspace` (P0)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Cycle: Y2026-H1 (반기)             Stage: SELF_REVIEW          [Edit Policy]│
├────────────────────────────────────────────────────────────────────────────┤
│ [PLANNED]─[ACTIVE]─[GOAL]─[MID]─●[SELF]─[MANAGER]─[CALIBRATION]─[FINAL]   │
├────────────────────────────────────────────────────────────────────────────┤
│ Stage Stats        Submit Rate: 78% (412/527)   Avg KPI score: 73.4       │
│ ┌───────────────┬───────────────┬───────────────┬──────────────────┐     │
│ │ KPI Submitted │ MBO Submitted │ Self Reviewed │ Calibration Pend │     │
│ │  412 / 527    │  398 / 527    │  321 / 527    │   ── (다음 단계)│     │
│ └───────────────┴───────────────┴───────────────┴──────────────────┘     │
├────────────────────────────────────────────────────────────────────────────┤
│ Department Rollup                                                          │
│  영업본부     ████████░░ 82%     Mfg ████████████ 95%   R&D ██████░░░ 65% │
│                                                                            │
│ [Transition to MANAGER_REVIEW]   [Send Reminders]   [Open Distribution]   │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 2: Manager `/manager/review/{cycleId}/employee/{empId}` (P0)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ 김OO  R&D팀 시니어 엔지니어    Cycle Y2026-H1                              │
├────────────────────────────────────────────────────────────────────────────┤
│ Tabs: [KPI Results] [MBO] [Competency] [360 Aggregate] [Self ↔ Manager]   │
├────────────────────────────────────────────────────────────────────────────┤
│ KPI Results                                                                │
│  ┌──────────────────┬────────┬────────┬──────────┬────────┬─────────┐    │
│  │ KPI              │ Weight │ Target │ Actual   │ Score  │ Evidence│    │
│  ├──────────────────┼────────┼────────┼──────────┼────────┼─────────┤    │
│  │ 신제품 출시 수    │  30%   │  3건   │  4건     │  100   │  📎     │    │
│  │ 품질불량률        │  25%   │ 0.5%   │ 0.6%     │  85    │  📎     │    │
│  │ ...               │  ...   │  ...   │  ...     │  ...   │  ...    │    │
│  └──────────────────┴────────┴────────┴──────────┴────────┴─────────┘    │
│  KPI Score: 91.2 / 100        Auto-aggregated weighted                    │
├────────────────────────────────────────────────────────────────────────────┤
│ Manager Comment                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐│
│  │  텍스트 입력…                                                         ││
│  └──────────────────────────────────────────────────────────────────────┘│
│                                                                            │
│ Preview Final Score:  91.2*0.6 + MBO 78*0.3 + Comp 82*0.1 = 86.5  →  A   │
│                                                                            │
│ [Save Draft]  [Submit Review]                                              │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 3: Director `/director/calibration/{cycleId}` (P0)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Calibration · R&D본부 · Y2026-H1     Stage: CALIBRATION  [Simulation Mode]│
├────────────────────────────────────────────────────────────────────────────┤
│ Current Distribution                Target Distribution                    │
│  S █░░░░░  5%                       S ██░░░░  10%                         │
│  A ████░░ 23%                       A ████░░  20%                         │
│  B ████████ 48%                     B ████████ 40%                         │
│  C ████░░░ 18%                      C ████░░  20%                         │
│  D ██░░░░  6%                       D ██░░░░  10%                         │
│                                                                            │
│ Drag employees between cells (Simulation only — not yet applied)          │
│ ┌──────────────────────────────────────────────────────────────────────┐  │
│ │ S (5)  │ A (23) │ B (48) │ C (18) │ D (6)                            │  │
│ │ ──────────────────────────────────────────────────                   │  │
│ │ 김OO │ 박OO │ 이OO …                                                │  │
│ │ ↓ drag                                                                │  │
│ └──────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│ [Preview Impact]   [Apply Distribution]   [Close Without Apply]           │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 4: Director `/director/kpi-tree/{cycleId}` BSC mode (P0)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ R&D본부 KPI Tree · Y2026-H1                            [BSC Mode: ON] ▼  │
├────────────────────────────────────────────────────────────────────────────┤
│  ┌──Financial──┬──Customer──┬──InternalProc──┬──Learning&Growth──┐        │
│  │ R&D ROI 130%│ NPS +12pt  │ 출시 사이클 -10%│ 스킬 인증율 90%  │CORP    │
│  │  ──────────────────────────────────────────────────────────  │        │
│  │ 매출 +8%    │ 고객 불만 0│ 품질불량 ≤0.5% │ 핵심인재 5명 양성 │DIV     │
│  │  ──────────────────────────────────────────────────────────  │        │
│  │ ...         │ ...         │ ...             │ ...               │TEAM    │
│  └─────────────┴─────────────┴─────────────────┴───────────────────┘        │
│                                                                            │
│ Σ Weight check: 100% ✓   Cascade integrity: 47/47 ✓                       │
│                                                                            │
│ [Add KPI Node]  [Edit Weights]  [Validate Cascade]                        │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 5: Employee `/my/okr/{cycleId}` (P1)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ My OKR · Y2026-H1                                          [+ New OKR]    │
├────────────────────────────────────────────────────────────────────────────┤
│ Objective 1: "데이터 플랫폼 안정성 확보"        Confidence: 8/10  ●●●●●●●●░░│
│  ├─ KR1: 평균 응답시간 ≤200ms (현재 240ms)        Score 0.0  ▱▱▱▱▱▱▱▱▱▱   │
│  ├─ KR2: 99.9% uptime 유지 (현재 99.92%)          Score 1.0  ███████████   │
│  └─ KR3: 인시던트 응답시간 ≤15분 (현재 18분)      Score 0.6  ██████░░░░░   │
│                                                                            │
│  Aligned with: Division OKR "테크 신뢰성 향상" →  Corp OKR "고객 신뢰"     │
│                                                                            │
│ Objective 2: "신규 마이크로서비스 3개 출시"        Confidence: 6/10 ●●●●●●░░│
│  ├─ KR1: …                                                                 │
│                                                                            │
│ [Update Progress] [Adjust Confidence] [View Alignment Tree]               │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 6: HR `/hr/cycles` 생성 모달 (P0)

```
┌─ New Evaluation Cycle ─────────────────────────────────────────────────┐
│ Code:     [ Y2026-H1            ]      Type:  ◉ HALF ○ QUARTER ○ ANNUAL│
│ Name:     [ 2026년 상반기 평가  ]                                       │
│ Period:   [ 2026-01-01 ] ~ [ 2026-06-30 ]                              │
├────────────────────────────────────────────────────────────────────────┤
│ Methods (Σ weight = 1.0)                                                │
│  ☑ KPI            weight [ 0.60 ]   mandatory ☑                        │
│  ☑ MBO            weight [ 0.30 ]   mandatory ☐                        │
│  ☑ Competency     weight [ 0.10 ]                                       │
│  ☐ OKR (보조)     weight [ 0.00 ]                                       │
├────────────────────────────────────────────────────────────────────────┤
│ Stages: ☑ GOAL_SETTING ☑ MID ☑ SELF ☑ MGR ☑ CALIBRATION ☑ FINAL ☑ APPEAL│
├────────────────────────────────────────────────────────────────────────┤
│ Rating Distribution                                                     │
│  Mode: ◉ FORCED  ○ ABSOLUTE  ○ HYBRID                                  │
│  S [10%] A [20%] B [40%] C [20%] D [10%]                                │
├────────────────────────────────────────────────────────────────────────┤
│ Options                                                                 │
│  ☑ Appeal enabled   Window: [ 14 ] days                                │
│  ☑ Self-reported achievement                                            │
│  ☑ BSC perspective (KPI Tree)                                           │
├────────────────────────────────────────────────────────────────────────┤
│                                          [ Cancel ]   [ Create Cycle ] │
└────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 7: Employee `/my/report/{cycleId}` 최종 리포트 (P0)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Y2026-H1  Performance Report          Published: 2026-07-15  Final         │
├────────────────────────────────────────────────────────────────────────────┤
│   Final Grade:   A                Score Breakdown                          │
│   ────────────                     KPI         91.2  × 0.60 = 54.7         │
│   Score:  86.5                     MBO         78.0  × 0.30 = 23.4         │
│                                    Competency  82.0  × 0.10 =  8.2         │
│   Rank:  Top 25%                   ─────────────────────────────           │
│                                    Total                       86.3 ≈ A   │
│                                                                            │
│ Manager Comment                                                            │
│  "전년 대비 …"                                                            │
│                                                                            │
│ Next Action                                                                │
│  - 시니어 리더십 교육 추천                                                 │
│  - CDP 1:1 일정 …                                                          │
│                                                                            │
│ [Acknowledge]   [Submit Appeal (within 14 days)]                           │
└────────────────────────────────────────────────────────────────────────────┘
```

#### Wireframe 8: Manager `/manager/talent-profile/{empId}` (P1)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Talent Profile: 김OO (R&D팀)                                              │
├────────────────────────────────────────────────────────────────────────────┤
│ Tabs: [Goals] [Reviews] [Competency] [Career] [Feedback] [Achievements]   │
├────────────────────────────────────────────────────────────────────────────┤
│ Goals (Current Cycle Y2026-H1)                                            │
│   KPI: 5건 (Σ weight 100%, 평균 진척 73%)                                 │
│   OKR: 2 Objective (8 KR, 평균 score 0.55)                                │
│                                                                            │
│ Reviews (지난 4 사이클)                                                    │
│   Y2025-H2: A (87.3)  Y2025-H1: B (76.5)  Y2024-H2: A (84.2)  ...        │
│                                                                            │
│ Competency Heat Map                                                        │
│   분석력 ████░░ 4    구조화 사고 █████░ 5    소통 ███░░░ 3                │
│                                                                            │
│ Career Track                                                               │
│   Current: Senior L3   Target: Lead L4   Gap: 리더십 역량 2점 +           │
│                                                                            │
│ [Recent Feedback] [View 1:1 History]                                       │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Part C — 사이클 운영 흐름 (시퀀스)

### C.1 정기 사이클 (반기/연간) 흐름 — 8 단계 표준

```
[Stage 0] PLANNED
   ↓ HR 어드민 → 사이클 생성 + 정책 설정 (가중치/분포/단계/옵션)
[Stage 1] ACTIVE — GOAL_SETTING (2주)
   ↓ 사원 KPI 트리 작성·MBO 합의·OKR 설정
   ↓ 매니저 cascade 확정 / KPI weight 검증
   ↓ 알림: 매주 진척 + D-7 마감 임박
[Stage 2] MID_REVIEW (1주)
   ↓ KpiActual 중간 입력·OKR progress 업데이트
   ↓ 매니저-사원 1:1 (Check-in)
[Stage 3] SELF_REVIEW (2주)
   ↓ PerformanceReview.SELF_PENDING → SELF_SUBMITTED
   ↓ 사원 KPI 자체 점수·MBO 답변·Competency self
   ↓ AchievementLog 누적분 매니저 confirm 마감
[Stage 4] MANAGER_REVIEW (2주)
   ↓ 매니저 평가 폼 (Wireframe 2) → 최종 점수 미리보기
   ↓ 매니저 코멘트 + competency 평가
[Stage 5] 360_COLLECTION (1주, 옵션)
   ↓ mra 송신 — 익명 360 진행
   ↓ mra → performance 수신 (집계만, min-rater-count 가드)
[Stage 6] CALIBRATION (1~2주)
   ↓ 부서장 + HR 회의 (Wireframe 3)
   ↓ 분포 시뮬레이션 → 매니저 점수 조정 → 합의 → lock
[Stage 7] FINALIZED → REPORT_PUBLISHED
   ↓ HR `/hr/reports/{cycleId}` 일괄 발행
   ↓ 사원 알림 + PerformanceReport append
   ↓ talent 송신 (PerformanceReview.FINALIZED → /api/internal/sync/performance-results)
[Stage 8] APPEAL_OPEN (14일, 옵션)
   ↓ 사원 Appeal 제출 → HR 검토 → DECISION
[Stage 9] CLOSED → ARCHIVED
```

### C.2 알림·권한·SLA 매트릭스

| 단계 | 알림 대상 | SLA | 권한 |
|------|----------|-----|------|
| GOAL_SETTING | 사원·매니저 (D-7, D-3, D-0) | 14일 | 사원 KPI/MBO/OKR 편집 |
| MID_REVIEW | 사원·매니저 | 7일 | KpiActual 추가 / Check-in 의무 |
| SELF_REVIEW | 사원 (D-7, D-3, D-1, D-0) | 14일 | PerformanceReview self section |
| MANAGER_REVIEW | 매니저 (D-7, D-3, D-1) | 14일 | manager section + competency |
| 360_COLLECTION | mra 위임 | 7일 | mra 권한 |
| CALIBRATION | 부서장·HR | 7~14일 | 부서장 조정 / HR lock |
| FINALIZED | HR / 사원 통지 | 즉시 | HR 발행 / 사원 view |
| APPEAL_OPEN | 사원 | 14일 | 사원 SUBMIT / HR 검토 |

### C.3 수시 사이클 (Check-in / Continuous Feedback / Recognition)

```
[매주 금요일] 주간 Check-in 알림 → 사원 입력
   ↓ 매니저 응답 의무 (3 영업일 내)
   ↓ 시간순 stream 누적

[연중 수시] 동료 Recognition / Feedback
   ↓ 동료간 즉시 입력 → wall 노출 (public/team/private)
   ↓ value tag 추출 → 사이클 결산 시 컬러 가중 검토 (옵션)
```

### C.4 사후 성과신고형 (한국)

```
[연중 수시] 사원 AchievementLog 등록 (성과·증빙)
   ↓ 매니저 confirm (SLA 7일)
   ↓ confirmed=true 만 사이클 결산 자동 합산
   ↓ 매니저 score 부여 (사이클 결산 시)
   ↓ PerformanceReview.kpiScore 또는 mboScore 에 가산 (정책에 따라)
```

### C.5 Calibration 회의 시퀀스 (P_DIR + P_HR)

```
1. HR /hr/calibration-sessions 에서 세션 생성 (부서장+HR 초대)
2. 회의 시작 — 매니저 점수 자동 분포 차트 노출
3. 본부장 시뮬레이션 모드 ON → 등급 이동 드래그
4. 분포 비교 (현재 vs 목표) → 합의
5. CONFIRMED → lock → PerformanceReview.CALIBRATION → FINALIZED 전이
6. audit chain 기록 (CalibrationSession.adjustmentLog JSONB)
```

---

## Part D — 인덱스·뷰·정합 게이트 요약

| 항목 | 정책 |
|------|------|
| tenant_id 선두 인덱스 | 모든 entity (기존 4 도메인 패턴 정합) |
| append-only entity | KpiActual, KeyResult(supersedes), CompetencyAssessment, PerformanceReport, Appeal, AchievementLog |
| 가중치 검증 | KpiNode/MboItem service-level @PrePersist (Σ=1.0) |
| 상태기계 검증 | EvaluationCycle, PerformanceReview, CalibrationSession, Appeal — 명시 전이 매트릭스 |
| ErrorCode | E98 (performance 점유 영역) 확장 — 신규 ~40 코드 |
| i18n | 5 locale × 도메인 키 (ko 풀 + en 풀 + ja/zh-CN/vi 부분) |
| S2S 수신 | hcm rm_*, mra ReportSnapshot, easy-job rm_skill_set — talent `STAGE15` 패턴 |
| S2S 송신 | talent `/api/internal/sync/performance-results` (FINALIZED 시) |

---

## 결론

- **16~24 신규 entity** (P0 12 + P1 6 + P2 6)
- **30 화면 + 5 페르소나** (P0 14 + P1 11 + P2 5)
- **사이클 운영 흐름**: 8 단계 정기 + 수시 Check-in + 사후 신고형 + Calibration 회의
- **기존 4 도메인 보존**: DB drop 없음, view + service-level mapping 또는 idempotent 분해
- **자매품 경계 준수**: hcm SoR / mra 익명 / easy-job 카탈로그 / talent 9-Box·승계

다음 문서(`04_roadmap_and_priority.md`)에서 P0~P2 단계 슬라이스 분해 + 결정 게이트 G_PERF_E1~E10 + 위험 5 + 다음 액션 3을 정의한다.
