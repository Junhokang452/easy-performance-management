> 작성 시각: 2026-06-11 / 작성자: Claude (easy-suite-orchestrator 산출물) / 목적: HR Tech 글로벌 SaaS 10종 화면 패턴 카탈로그 + performance 채택 권고 박제

# 02. Market Research — 글로벌 SaaS 10종 화면 패턴 + 한국 시장 참고 + 채택 패턴 9종

## §1 글로벌 SaaS 10종 핵심 화면 패턴 카탈로그

### 1.1 SAP SuccessFactors (엔터프라이즈 1위, 강제 분포·calibration 표준)

| 화면 | 주요 요소 | 인터랙션 | performance 참고 |
|------|-----------|----------|------------------|
| **Performance Form** | Goal section + Competency section + Self / Manager 양분 + Signature 단계 | Stepper 진행, 단계별 lock | ✅ PerformanceReview 폼 모델 |
| **Calibration Grid** | 부서×등급 매트릭스, 매니저 점수 vs 분포 시뮬레이션, 드래그로 등급 이동 | 시뮬레이션 모드 — 적용 전 영향 미리보기 | ✅ RatingDistribution 시뮬레이터 |
| **Goal Plan** | 가중치 합 100% 검증, 목표 트리, 진척 % 슬라이더 | 가중치 합 위반 시 경고, in-flight 수정 가능(cycle stage 따라) | ✅ KPI Tree + Mbo |
| **9-Box Talent Review** | 9칸 그리드, 사원 카드 드래그 | 부서장 합의 회의용, 회의 후 lock | ⚠️ talent 위임 — performance 는 시각화만 |
| **Continuous Performance** | 1:1 노트 + Achievement + Feedback 카드 stream | 시간순 stream + 필터 | ✅ CheckIn + Achievement stream |

### 1.2 Workday (엔터프라이즈, talent profile 강점)

| 화면 | 주요 요소 | 인터랙션 |
|------|-----------|----------|
| **Talent Profile** | 사원 단일 페이지: Goals + Competencies + Reviews + Career + Skills | 탭 또는 long scroll, 사원/매니저 view 권한 분기 |
| **Performance Review** | Self section + Manager section + 360 input + Final rating | 단계별 권한, sub-aggregate FE 구조 |
| **Calibration Session** | 회의실 모드: 참가자 다중 + 등급 분포 실시간 + 매니저 의견 노출 | 회의 mode lock |
| **Goal Tree** | cascade 트리 + alignment 화살표 | 드래그 alignment 가능 |

### 1.3 Lattice (북미 mid-market 1위, OKR + Review 통합)

| 화면 | 주요 요소 | 인터랙션 |
|------|-----------|----------|
| **OKR Tree** | Objective + 3~5 KR + alignment 표시 + progress bar + confidence | 노드 클릭 → 사이드 패널(편집·comment·history) |
| **Reviews** | Self / Manager / Upward / Peer 4 모드 + 시즌 진행 stepper | 시즌별 questionnaire 동적 구성 |
| **Updates (Weekly)** | Check-in 카드 stream + OKR 자동 사전 채움 | 주간 폼 (priorities, blockers, accomplishments) |
| **Praise** | 동료 칭찬 wall + value tag + emoji | wall + 개인 알림 |
| **Career Track** | Competency framework + 사원 현 단계 + 다음 단계 GAP | 매니저-사원 1:1 회고 도구 |
| **Box View** | 9-Box (성과×잠재력) + 축 customizable | calibration session 진입 |

### 1.4 15Five (mid-market, 주간 check-in 표준)

| 화면 | 주요 요소 | 인터랙션 |
|------|-----------|----------|
| **Check-in (Weekly)** | 우선순위·성과·blocker·sentiment 1~5 | 매주 금요일 알림, 매니저 응답 의무 |
| **Objectives** | OKR + KR + Update | 주간 자동 진척 prompt |
| **HighFives** | 동료 칭찬 + value tag + 공개 stream | 슬랙 연동 |
| **1-on-1** | 매니저-팀원 agenda 공유, 메모 노트 | 양방향 편집, 의무 보존 |
| **Best-Self Review** | 강점 + 성장 + 자기인식 + 매니저 인식 | 연 1회 |

### 1.5 Culture Amp (engagement 강점)

| 화면 | 주요 요소 |
|------|-----------|
| **Goals** | OKR 표준 + 회사·팀·개인 cascade |
| **Self-Reflection** | 회고 폼 + 강점/약점/성장 |
| **Manager Reflection** | 매니저 평가 폼 + 비교 view |
| **Calibration** | 부서장 회의 + 분포 조정 |
| **Engagement Pulse** | 짧은 설문 + 결과 dashboard |

### 1.6 Quantive (Gtmhub) — OKR 전문

| 화면 | 주요 요소 |
|------|-----------|
| **OKR Tree** | 다단계 + 협력 OKR (다중 owner) |
| **Insights** | 자동 데이터 수집 + KR 자동 업데이트 |
| **Whiteboard** | OKR 기획 워크숍 모드 (스티커 + 토론) |
| **Sessions** | 주간/월간 검토 회의 view |

### 1.7 Profit.co — OKR + KPI 통합

| 화면 | 주요 요소 |
|------|-----------|
| **Aligned OKR** | OKR 트리 + alignment 표시 |
| **KPI Boards** | 별도 화면 — KPI 카드 dashboard |
| **1-on-1** | 매니저-팀원 미팅 도구 |
| **Performance Reviews** | 사이클 기반 + 360 옵션 |
| **Task** | OKR 하위 task tracker |

→ **KPI / OKR 분리 화면 모범** — performance 가 채택해야 할 패턴

### 1.8 BetterWorks — Continuous Performance

| 화면 | 주요 요소 |
|------|-----------|
| **Goal Network** | 사원-사원 연결 그래프 (단순 트리가 아닌 네트워크) |
| **Conversations** | 1:1 + 코칭 노트 |
| **Recognition** | badge + value tag + 공개 인정 |

### 1.9 Leapsome — Performance + Learning 통합

| 화면 | 주요 요소 |
|------|-----------|
| **Goals** | OKR + Cascade |
| **Reviews** | 360 + Self + Manager + Upward 4 type |
| **1:1s** | 미팅 도구 + agenda 공유 |
| **Praise** | 동료 칭찬 |
| **Learning** | 사내 교육 path (역량 기반 추천) |

### 1.10 Mirro — OKR + Praise + Feedback + 1:1

| 화면 | 주요 요소 |
|------|-----------|
| **OKR** | 표준 + alignment |
| **Praise** | 동료 인정 |
| **Feedback** | 즉시·익명 옵션 |
| **1-on-1** | 미팅 노트 |

---

## §2 한국 시장 SaaS 참고

| 도구 | 핵심 기능 | 한국 특수성 흡수 |
|------|----------|------------------|
| **Klever (구 Hailley)** | KPI + MBO + 강제 분포 + Calibration + 이의신청 | 등급 분포 시뮬레이터, 보상 매트릭스 연동 |
| **flex (HR 슈트)** | 평가 모듈 — KPI + MBO + 다면평가 + 사후 성과신고 | "성과 신고서" 폼 + 매니저 confirm |
| **시프티 인사평가** | KPI + 직무역량 + 등급 산출 + PI 매트릭스 | 강제 분포 + 호봉 매트릭스 연동 |
| **신한 HR** | KPI + 360° + 인사위원회 | 인사위원회 워크플로우, audit 강함 |
| **사람인 / 인크루트 HR** | Mid 시장 KPI + 평가 + 보상 | 보상 연동 자동화 |

**공통 패턴 (한국)**:
- 강제 분포(S/A/B/C/D) 표준
- 이의신청 단계 의무
- 사후 성과신고 (한국·일본 문화)
- 직무역량 분리 (HRD 부서)
- 매니저 다음 단계 = 부서장 = 차상위 = HR 위원회 4단계 결재 라인

---

## §3 화면 패턴 9종 추출 (performance 채택 권고)

### Pattern 1: **사이클 Stepper + 단계별 lock** (SAP / Workday / Lattice)

- 사이클 진행 시각화: GOAL_SETTING → MID → SELF → MANAGER → 360 → CALIBRATION → FINALIZED → APPEAL
- 단계별 권한 lock — 단계 외 편집 차단
- → performance 채택 ✅ (P0)

### Pattern 2: **KPI Tree (cascade + BSC 라벨)** (Profit.co / Workday)

- 트리 노드 = KpiNode, 부모-자식 cascade, 가중치 합 100% 검증
- BSC 4 관점 라벨 컬럼 옵션
- → performance 채택 ✅ (P0)

### Pattern 3: **OKR Tree + Alignment 화살표** (Lattice / Quantive / Profit.co)

- Objective 노드 + 3~5 KR 카드 + alignment(상위 OKR) 화살표
- 노드 클릭 → 사이드 패널 (편집·comment·history·confidence)
- → performance 채택 ✅ (P1)

### Pattern 4: **Performance Review Form (Self + Manager 양분 + 가중 합산)** (SAP / Workday)

- KPI 섹션 + Competency 섹션 + Self 답변 ↔ Manager 답변 옆 비교
- 가중치 자동 계산 → 최종 점수 미리보기
- → performance 채택 ✅ (P0)

### Pattern 5: **Calibration Grid (강제 분포 시뮬레이터)** (SAP / Klever)

- 매니저 점수 → 분포 시뮬레이션 → 시각화(현재 vs 목표 분포) → 매니저 조정 모드
- 조정 후 lock + 매니저 합의 서명
- → performance 채택 ✅ (P0) — talent 9-Box 와 다른 사용처 (등급 분포만)

### Pattern 6: **Weekly Check-in 카드 stream** (15Five / Lattice)

- 주간 폼 (priorities, blockers, sentiment) + OKR 자동 사전 채움
- 매니저 응답 카드 + 시간순 stream
- → performance 채택 ✅ (P1)

### Pattern 7: **Praise / Recognition Wall** (Lattice / 15Five / BetterWorks)

- 동료 칭찬 카드 + value/competency tag + 공개 wall
- 슬랙/팀즈 연동
- → performance 채택 ✅ (P2)

### Pattern 8: **Career Track / Competency Framework** (Lattice / Leapsome)

- 직무 layer × 단계 × competency 격자 + 사원 현 단계 표시
- 다음 단계 GAP 시각화
- → performance 채택 ✅ (P1, easy-job-management 카탈로그 수신)

### Pattern 9: **Talent Profile (사원 단일 페이지)** (Workday)

- 한 사원의 모든 데이터: Goals + Reviews + Competency + Career + Feedback history
- 매니저 1:1 준비용 단일 화면
- → performance 채택 ✅ (P1)

### (참고) Pattern 10: **9-Box Talent Review**

- talent 가 SoR. performance 는 자체 만들지 않음 — talent FE 의 9-Box Matrix Grid 재사용 또는 link out.

---

## §4 채택 패턴별 자매품 경계

| 패턴 | performance | 다른 자매품 |
|------|-------------|------------|
| 1. 사이클 Stepper | ✅ SoR | — |
| 2. KPI Tree | ✅ SoR | — |
| 3. OKR Tree | ✅ SoR | — |
| 4. Review Form | ✅ SoR | — |
| 5. Calibration Grid | ✅ (등급 분포) | talent (9-Box 별도) |
| 6. Check-in | ✅ SoR | — |
| 7. Recognition Wall | ✅ SoR | — |
| 8. Career Track | ✅ (binding view) | easy-job-management (SkillSet/Skill SoR) |
| 9. Talent Profile | ✅ (composite view) | hcm (employee SoR), talent (succession), mra (360 결과 집계) |
| (10. 9-Box) | ⚠️ link out only | **talent SoR** |

---

## §5 화면 우선순위 권고

| 우선순위 | 화면 카테고리 | 채택 패턴 |
|---------|--------------|-----------|
| **P0** | 사이클 운영 + KPI + 정기 평가 | 1, 2, 4, 5 |
| **P1** | OKR + Check-in + 역량 + 사원 종합 view | 3, 6, 8, 9 |
| **P2** | 인정 + 동료 피드백 + 문화 | 7 |

---

## §6 인터랙션 모범 (글로벌 SaaS 공통)

- **사이드 패널 편집**: 트리 노드 클릭 → 오른쪽 슬라이드 패널 (Mantine Drawer)
- **자동 진척**: KR 자동 업데이트 (Quantive Insights 패턴 — 본 자매품 P2 검토)
- **stream 시간순**: Check-in / Feedback / Recognition 모두 시간순 카드
- **권한 분기**: 사원·매니저·HR 어드민 각 view 분기 — 단일 화면 다중 모드 (Mantine SegmentedControl)
- **드래그 인터랙션**: Calibration / 9-Box / 분포 조정 (Mantine Drag) — talent FE 가 이미 사용중인 패턴
- **단계 lock**: 사이클 단계 외 편집 차단 (read-only badge)
- **시뮬레이션 모드**: 적용 전 영향 미리보기 (강제 분포)
- **공개 wall**: 동료 칭찬 — 사기 진작

---

## §7 결론

- 글로벌 SaaS 10종이 **사이클 + KPI + OKR + Calibration + Check-in + Recognition + Career Track** 7 영역으로 수렴
- performance 는 9 패턴을 P0~P2 단계로 채택 — 자매품 경계 보존
- 한국 시장 특수성(강제 분포·이의신청·사후 성과신고)은 **EvaluationPolicy 옵션 + RatingDistribution + Appeal + AchievementLog** 4 카드로 P0~P1 흡수
- 9-Box / Calibration 최종 산출 = **talent 위임** (`f3edc12` 이후 talent FE 9-Box Matrix Grid 실존)
- 360 익명 다면 = **mra 위임** (`dbdcf96` 패턴, min-rater-count 가드)

다음 문서(`03_domain_model_and_screens.md`)에서 도메인 모델 16 entity + 페르소나 5 × 화면 30 매트릭스 + 사이클 시퀀스를 정의한다.
