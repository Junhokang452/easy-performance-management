# P0-S2 계약서 — KPI 도메인 (KpiTree / KpiNode / KpiAssignment / KpiActual)

> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / BE·FE 에이전트 공동 SoT — **이탈 금지**
> 결정 적용: G_PERF_E2 (단계적 A — P0 = MANUAL 만, source enum 박제) + G_PERF_E5 (B — cycle.bscEnabled + KpiNode.bscPerspective nullable)
> ERD SoT: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md` Part A Entity 3~6

## 1. Entity 4건

### KpiTree (`kpi_tree`)
- `id` UUID PK (UuidV7) / `tenant_id` (TenantAwareAuditEntity)
- `cycle_id` UUID NOT NULL FK → evaluation_cycle **ON DELETE CASCADE**
- `owner_org_unit_id` UUID NULL (rm_org_unit 은 P0-S6 수신 — FK 없는 plain UUID)
- `name` varchar(100) NOT NULL
- `level` varchar(20) enum `KpiTreeLevel`: CORPORATE | DIVISION | TEAM | INDIVIDUAL
- `bsc_enabled` boolean NOT NULL default false
- 인덱스: `(tenant_id, cycle_id, owner_org_unit_id)`

### KpiNode (`kpi_node`)
- `id` / `tenant_id`
- `tree_id` UUID NOT NULL FK → kpi_tree **ON DELETE CASCADE**
- `parent_id` UUID NULL FK → kpi_node(id) (self) — **무결성: parent 는 같은 tree 소속** (service 검증)
- `label` varchar(200) NOT NULL
- `weight` numeric(5,4) NOT NULL — CHECK `weight > 0 AND weight <= 1`
- `target` numeric(18,4) NULL / `unit` varchar(20) NULL
- `bsc_perspective` varchar(20) NULL enum `BscPerspective`: FINANCIAL | CUSTOMER | INTERNAL_PROCESS | LEARNING_GROWTH (nullable = 미지정)
- `source` varchar(20) NOT NULL default MANUAL enum `KpiNodeSource`: MANUAL | HCM | EXTERNAL — **P0 는 MANUAL 외 거부** (E9804239)
- `source_config` jsonb NULL (P1 박제 — P0 미사용)
- `cascade_from_id` UUID NULL (상위 트리 KPI 참조 — FK 없음, cross-tree)
- 인덱스: `ix_kpi_node_tenant_tree_parent (tenant_id, tree_id, parent_id)`

### KpiAssignment (`kpi_assignment`)
- `id` / `tenant_id`
- `kpi_node_id` UUID NOT NULL FK → kpi_node **ON DELETE CASCADE**
- `employee_id` UUID NOT NULL (rm_employee 는 P0-S6 — plain UUID)
- `weight` numeric(5,4) NULL (개인 override — null 이면 node.weight) — CHECK 동일 (NULL 허용)
- `target_override` numeric(18,4) NULL
- UNIQUE `(tenant_id, kpi_node_id, employee_id)` → 위반 E9804924
- 인덱스: `(tenant_id, employee_id)` (my KPI 조회)

### KpiActual (`kpi_actual`) — append-only
- `id` / `tenant_id`
- `kpi_assignment_id` UUID NOT NULL FK → kpi_assignment **ON DELETE CASCADE**
- `as_of_date` date NOT NULL
- `actual_value` numeric(18,4) NOT NULL
- `source` varchar(20) NOT NULL default MANUAL enum `KpiActualSource`: MANUAL | AUTO | IMPORT — P0 는 서버에서 MANUAL 고정
- `reported_by` UUID NULL / `evidence_url` varchar(500) NULL / `comment` text NULL
- `supersedes_id` UUID NULL **UNIQUE** FK → kpi_actual(id) (talent ReviewDecision 패턴 — 정정 = 신규 row)
- UPDATE/DELETE 없음 (정정은 supersede 전용)
- 인덱스: `(tenant_id, kpi_assignment_id, as_of_date DESC)`

## 2. Flyway

`backend/src/main/resources/db/migration/V20260611_002__kpi.sql` — V20260611_001 의 audit 컬럼 DDL 패턴(created_at/created_by/updated_at/updated_by/tenant_id) 그대로 복제.

## 3. 검증 규칙 (service-level)

| 규칙 | ErrorCode | HTTP |
|------|-----------|------|
| tree 없음 | KPI_TREE_NOT_FOUND `E9804443` | 404 |
| node 없음 | KPI_NODE_NOT_FOUND `E9804444` | 404 |
| assignment 없음 | KPI_ASSIGNMENT_NOT_FOUND `E9804445` | 404 |
| actual 없음 | KPI_ACTUAL_NOT_FOUND `E9804446` | 404 |
| parent 가 다른 tree 소속 | KPI_NODE_PARENT_TREE_MISMATCH `E9804236` | 422 |
| weight ∉ (0,1] | KPI_WEIGHT_OUT_OF_RANGE `E9804237` | 422 |
| 형제 weight 합 > 1.0+0.001 (쓰기 시) | KPI_WEIGHT_SUM_EXCEEDED `E9804238` | 422 |
| node source ≠ MANUAL (P0) | KPI_SOURCE_NOT_SUPPORTED `E9804239` | 422 |
| assignment (node×employee) 중복 | KPI_ASSIGNMENT_DUPLICATE `E9804924` | 409 |
| 이미 supersede 된 actual 재정정 | KPI_ACTUAL_ALREADY_SUPERSEDED `E9804925` | 409 |
| 자식 있는 node delete | KPI_NODE_HAS_CHILDREN `E9804926` | 409 |
| cycle FINALIZED/CANCELLED 에서 일체 쓰기 | KPI_CYCLE_LOCKED `E9804927` | 409 |

가중치 정합 정책: **쓰기 시 합 ≤ 1.0 가드(초과만 거부)** + 조회 응답에 per-parent `childWeightSum`/`childWeightComplete(합==1.0±0.001)` 노출 — 트리 점진 구축 중 합 < 1.0 은 허용(완결성은 FE 뱃지로 가시화). ERD §A.3 @PrePersist 권고의 실무 보정.

파생 계산: effectiveWeight = assignment.weight ?? node.weight / effectiveTarget = targetOverride ?? node.target / latestActual = supersede 안 된 row 중 max(asOfDate, createdAt) / achievementRate = latestActualValue ÷ effectiveTarget (target null·0 또는 actual 없음 → null).

## 4. REST 계약 (base `/api/v1`)

| HTTP | Path | Body | 응답 |
|------|------|------|------|
| GET | `/cycles/{cycleId}/kpi-trees` | — | `List<KpiTreeResponse>` |
| POST | `/cycles/{cycleId}/kpi-trees` | `KpiTreeCreateRequest {name, level, ownerOrgUnitId?, bscEnabled?}` | 201 `KpiTreeResponse` |
| GET | `/kpi-trees/{treeId}` | — | `KpiTreeDetailResponse` (tree 필드 + `nodes: KpiNodeResponse[]` flat) |
| PATCH | `/kpi-trees/{treeId}` | `KpiTreeUpdateRequest {name?, level?, ownerOrgUnitId?, bscEnabled?}` | `KpiTreeResponse` |
| DELETE | `/kpi-trees/{treeId}` | — | 204 |
| POST | `/kpi-trees/{treeId}/nodes` | `KpiNodeCreateRequest {parentId?, label, weight, target?, unit?, bscPerspective?, source?, cascadeFromId?}` | 201 `KpiNodeResponse` |
| PATCH | `/kpi-nodes/{nodeId}` | `KpiNodeUpdateRequest {label?, weight?, target?, unit?, bscPerspective?}` | `KpiNodeResponse` |
| DELETE | `/kpi-nodes/{nodeId}` | — | 204 |
| GET | `/kpi-nodes/{nodeId}/assignments` | — | `List<KpiAssignmentResponse>` |
| POST | `/kpi-nodes/{nodeId}/assignments` | `KpiAssignmentCreateRequest {employeeId, weight?, targetOverride?}` | 201 `KpiAssignmentResponse` |
| PATCH | `/kpi-assignments/{assignmentId}` | `KpiAssignmentUpdateRequest {weight?, targetOverride?}` | `KpiAssignmentResponse` |
| DELETE | `/kpi-assignments/{assignmentId}` | — | 204 |
| GET | `/kpi-assignments/my?cycleId=&employeeId=` | — | `List<MyKpiAssignmentResponse>` (둘 다 필수 — SelfEvaluation employeeId 쿼리 파라미터 패턴 정합, principal 주입은 P0-S6 이후) |
| GET | `/kpi-assignments/{assignmentId}/actuals` | — | `List<KpiActualResponse>` (asOfDate DESC) |
| POST | `/kpi-assignments/{assignmentId}/actuals` | `KpiActualCreateRequest {asOfDate, actualValue, evidenceUrl?, comment?}` | 201 `KpiActualResponse` |
| POST | `/kpi-actuals/{actualId}/supersede` | `KpiActualSupersedeRequest {asOfDate?, actualValue, evidenceUrl?, comment?}` | 201 `KpiActualResponse` (신규 row, supersedesId=원본) |

### Response shape (camelCase JSON)

```
KpiTreeResponse        {id, cycleId, name, level, ownerOrgUnitId, bscEnabled, createdAt, updatedAt}
KpiTreeDetailResponse  {…KpiTreeResponse, nodes: KpiNodeResponse[]}
KpiNodeResponse        {id, treeId, parentId, label, weight, target, unit, bscPerspective, source,
                        cascadeFromId, childWeightSum, childWeightComplete, assignmentCount, createdAt, updatedAt}
KpiAssignmentResponse  {id, kpiNodeId, employeeId, weight, targetOverride, createdAt, updatedAt}
MyKpiAssignmentResponse{id, kpiNodeId, nodeLabel, treeId, treeName, cycleId, weight, target, unit,
                        bscPerspective, source, latestActualValue, latestActualAsOfDate, achievementRate}
                        (weight/target = effective 값)
KpiActualResponse      {id, kpiAssignmentId, asOfDate, actualValue, source, reportedBy, evidenceUrl,
                        comment, supersedesId, superseded, createdAt}
```

## 5. FE 화면 3종 (카탈로그 #2/#13/#18)

| 화면 | 라우트 | 내용 |
|------|--------|------|
| My KPI (#2) | `/my/kpi` | cycle Select + employeeId 입력(SelfEvaluationPage 패턴) → my assignments 카드/테이블 (effective weight·target·최신실적·달성률) + 실적 입력 모달 + 실적 이력 모달(supersede 정정) |
| 매니저 KPI Tree (#13) | `/manager/kpi-tree` | cycle Select → tree 목록·생성 → 트리 렌더(부모-자식 들여쓰기) + per-parent weight 합 뱃지(complete/incomplete/초과) + node CRUD 모달 + node 별 assignment 관리 모달. 드래그·드롭 cascade 재배치는 P1 보류 |
| 본부 KPI Tree (#18) | `/director/kpi-tree` | read-only 트리 + **BSC ON/OFF 토글** — ON 이면 bscPerspective 4 관점 컬럼 그룹핑(+미지정 컬럼). tree.bscEnabled 가 기본값 |

공유 컴포넌트: `pages/kpi/` 아래 KpiNodeTree(재귀 렌더, readOnly prop), NodeFormModal, AssignmentModal, ActualFormModal, errorMapping.ts (E9804 12종). API 모듈 `src/api/kpi.ts` (kpiQueryKeys + RQ 훅 — cycles.ts 패턴 정합).

i18n: ko + en 풀 (E10 P0 단계) — `nav.*` 3키 + `kpi.*` + `error.E9804xxx` 12키 parity.

## 6. 게이트

- BE: `gradlew compileJava` + `gradlew test` 전체 (기존 44 회귀 0 + 신규 ≥15)
- FE: `npm run typecheck` + `npm run build` (lazy chunk 분리 확인)
- 표준: STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY + ADR-026 명명 + ADR-027 i18n 네임스페이스
