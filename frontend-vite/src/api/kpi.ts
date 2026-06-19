/**
 * KPI 도메인 API — P0-S2 슬라이스 (KpiTree / KpiNode / KpiAssignment / KpiActual).
 *
 * 계약 SoT: `_workspace/00_input/p0_s2_contract.md` §4 (REST 17 endpoint + camelCase 응답 shape).
 *
 * BE 정합:
 * - base path: `/api/v1`
 * - 트리 상세 응답: `KpiTreeDetailResponse { ...tree, nodes: KpiNodeResponse[] }` (flat 배열, FE 가 parentId 로 조립)
 * - my assignments: `/kpi-assignments/my?cycleId=&employeeId=` (둘 다 필수, principal 주입은 P0-S6 이후)
 * - actual 정정: supersede 전용 (UPDATE/DELETE 없음 — talent ReviewDecision append-only 패턴)
 *
 * 파생 계산(effectiveWeight·achievementRate·childWeightSum 등)은 **BE 가 응답에 포함** — FE 재계산 금지(표시만).
 *
 * 자매품 정합: cycles.ts / selfEvaluation.ts 패턴.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { BscPerspective } from './kpiEnums';

export type {
  KpiTreeLevel,
  BscPerspective,
  KpiNodeSource,
  KpiActualSource,
} from './kpiEnums';
export {
  ALL_KPI_TREE_LEVELS,
  ALL_BSC_PERSPECTIVES,
  ALL_KPI_NODE_SOURCES,
} from './kpiEnums';

import type {
  KpiActualSource,
  KpiNodeSource,
  KpiTreeLevel,
} from './kpiEnums';

// ---------- Response types (camelCase, 계약 §4) ----------

export interface KpiTreeResponse {
  id: string;
  cycleId: string;
  name: string;
  level: KpiTreeLevel;
  ownerOrgUnitId: string | null;
  bscEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface KpiNodeResponse {
  id: string;
  treeId: string;
  parentId: string | null;
  label: string;
  weight: number;
  target: number | null;
  unit: string | null;
  bscPerspective: BscPerspective | null;
  source: KpiNodeSource;
  cascadeFromId: string | null;
  childWeightSum: number;
  childWeightComplete: boolean;
  assignmentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface KpiTreeDetailResponse extends KpiTreeResponse {
  nodes: KpiNodeResponse[];
}

export interface KpiAssignmentResponse {
  id: string;
  kpiNodeId: string;
  employeeId: string;
  weight: number | null;
  targetOverride: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface MyKpiAssignmentResponse {
  id: string;
  kpiNodeId: string;
  nodeLabel: string;
  treeId: string;
  treeName: string;
  cycleId: string;
  weight: number | null; // effective
  target: number | null; // effective
  unit: string | null;
  bscPerspective: BscPerspective | null;
  source: KpiNodeSource;
  latestActualValue: number | null;
  latestActualAsOfDate: string | null;
  achievementRate: number | null;
}

export interface KpiActualResponse {
  id: string;
  kpiAssignmentId: string;
  asOfDate: string;
  actualValue: number;
  source: KpiActualSource;
  reportedBy: string | null;
  evidenceUrl: string | null;
  comment: string | null;
  supersedesId: string | null;
  superseded: boolean;
  createdAt: string;
}

// ---------- Request types ----------

export interface KpiTreeCreateRequest {
  name: string;
  level: KpiTreeLevel;
  ownerOrgUnitId?: string | null;
  bscEnabled?: boolean;
}

export interface KpiTreeUpdateRequest {
  name?: string;
  level?: KpiTreeLevel;
  ownerOrgUnitId?: string | null;
  bscEnabled?: boolean;
}

export interface KpiNodeCreateRequest {
  parentId?: string | null;
  label: string;
  weight: number;
  target?: number | null;
  unit?: string | null;
  bscPerspective?: BscPerspective | null;
  source?: KpiNodeSource;
  cascadeFromId?: string | null;
}

export interface KpiNodeUpdateRequest {
  label?: string;
  weight?: number;
  target?: number | null;
  unit?: string | null;
  bscPerspective?: BscPerspective | null;
}

export interface KpiAssignmentCreateRequest {
  employeeId: string;
  weight?: number | null;
  targetOverride?: number | null;
}

export interface KpiAssignmentUpdateRequest {
  weight?: number | null;
  targetOverride?: number | null;
}

export interface KpiActualCreateRequest {
  asOfDate: string;
  actualValue: number;
  evidenceUrl?: string | null;
  comment?: string | null;
}

export interface KpiActualSupersedeRequest {
  asOfDate?: string | null;
  actualValue: number;
  evidenceUrl?: string | null;
  comment?: string | null;
}

const BASE = '/v1';

// ---------- Query keys (ADR-026 / cycles.ts 정합) ----------

export const kpiQueryKeys = {
  all: () => buildQueryKey('performance', 'kpi'),
  trees: (cycleId: string) =>
    buildQueryKey('performance', 'kpi', 'trees', cycleId),
  treeDetail: (treeId: string) =>
    buildQueryKey('performance', 'kpi', 'tree', treeId),
  nodeAssignments: (nodeId: string) =>
    buildQueryKey('performance', 'kpi', 'node', nodeId, 'assignments'),
  my: (cycleId: string, employeeId: string) =>
    buildQueryKey('performance', 'kpi', 'my', { cycleId, employeeId }),
  assignmentActuals: (assignmentId: string) =>
    buildQueryKey('performance', 'kpi', 'assignment', assignmentId, 'actuals'),
} as const;

// ---------- API ----------

export const kpiApi = {
  listTrees: (cycleId: string) =>
    apiClient
      .get<KpiTreeResponse[]>(`${BASE}/cycles/${cycleId}/kpi-trees`)
      .then((r) => r.data),

  createTree: (cycleId: string, req: KpiTreeCreateRequest) =>
    apiClient
      .post<KpiTreeResponse>(`${BASE}/cycles/${cycleId}/kpi-trees`, req)
      .then((r) => r.data),

  getTree: (treeId: string) =>
    apiClient
      .get<KpiTreeDetailResponse>(`${BASE}/kpi-trees/${treeId}`)
      .then((r) => r.data),

  updateTree: (treeId: string, req: KpiTreeUpdateRequest) =>
    apiClient
      .patch<KpiTreeResponse>(`${BASE}/kpi-trees/${treeId}`, req)
      .then((r) => r.data),

  deleteTree: (treeId: string) =>
    apiClient.delete<void>(`${BASE}/kpi-trees/${treeId}`).then((r) => r.data),

  createNode: (treeId: string, req: KpiNodeCreateRequest) =>
    apiClient
      .post<KpiNodeResponse>(`${BASE}/kpi-trees/${treeId}/nodes`, req)
      .then((r) => r.data),

  updateNode: (nodeId: string, req: KpiNodeUpdateRequest) =>
    apiClient
      .patch<KpiNodeResponse>(`${BASE}/kpi-nodes/${nodeId}`, req)
      .then((r) => r.data),

  deleteNode: (nodeId: string) =>
    apiClient.delete<void>(`${BASE}/kpi-nodes/${nodeId}`).then((r) => r.data),

  listNodeAssignments: (nodeId: string) =>
    apiClient
      .get<KpiAssignmentResponse[]>(`${BASE}/kpi-nodes/${nodeId}/assignments`)
      .then((r) => r.data),

  createAssignment: (nodeId: string, req: KpiAssignmentCreateRequest) =>
    apiClient
      .post<KpiAssignmentResponse>(
        `${BASE}/kpi-nodes/${nodeId}/assignments`,
        req,
      )
      .then((r) => r.data),

  updateAssignment: (assignmentId: string, req: KpiAssignmentUpdateRequest) =>
    apiClient
      .patch<KpiAssignmentResponse>(
        `${BASE}/kpi-assignments/${assignmentId}`,
        req,
      )
      .then((r) => r.data),

  deleteAssignment: (assignmentId: string) =>
    apiClient
      .delete<void>(`${BASE}/kpi-assignments/${assignmentId}`)
      .then((r) => r.data),

  listMy: (cycleId: string, employeeId: string) =>
    apiClient
      .get<MyKpiAssignmentResponse[]>(`${BASE}/kpi-assignments/my`, {
        params: { cycleId, employeeId },
      })
      .then((r) => r.data),

  listActuals: (assignmentId: string) =>
    apiClient
      .get<KpiActualResponse[]>(
        `${BASE}/kpi-assignments/${assignmentId}/actuals`,
      )
      .then((r) => r.data),

  createActual: (assignmentId: string, req: KpiActualCreateRequest) =>
    apiClient
      .post<KpiActualResponse>(
        `${BASE}/kpi-assignments/${assignmentId}/actuals`,
        req,
      )
      .then((r) => r.data),

  supersedeActual: (actualId: string, req: KpiActualSupersedeRequest) =>
    apiClient
      .post<KpiActualResponse>(`${BASE}/kpi-actuals/${actualId}/supersede`, req)
      .then((r) => r.data),
};

// ---------- RQ hooks ----------

export function useKpiTreesQuery(cycleId: string | null | undefined) {
  return useQuery({
    queryKey: kpiQueryKeys.trees(cycleId ?? ''),
    queryFn: () => kpiApi.listTrees(cycleId as string),
    enabled: Boolean(cycleId),
  });
}

export function useKpiTreeDetailQuery(treeId: string | null | undefined) {
  return useQuery({
    queryKey: kpiQueryKeys.treeDetail(treeId ?? ''),
    queryFn: () => kpiApi.getTree(treeId as string),
    enabled: Boolean(treeId),
  });
}

export function useNodeAssignmentsQuery(nodeId: string | null | undefined) {
  return useQuery({
    queryKey: kpiQueryKeys.nodeAssignments(nodeId ?? ''),
    queryFn: () => kpiApi.listNodeAssignments(nodeId as string),
    enabled: Boolean(nodeId),
  });
}

export function useMyKpiAssignmentsQuery(
  cycleId: string | null | undefined,
  employeeId: string | null | undefined,
) {
  return useQuery({
    queryKey: kpiQueryKeys.my(cycleId ?? '', employeeId ?? ''),
    queryFn: () => kpiApi.listMy(cycleId as string, employeeId as string),
    enabled: Boolean(cycleId) && Boolean(employeeId),
  });
}

export function useAssignmentActualsQuery(
  assignmentId: string | null | undefined,
) {
  return useQuery({
    queryKey: kpiQueryKeys.assignmentActuals(assignmentId ?? ''),
    queryFn: () => kpiApi.listActuals(assignmentId as string),
    enabled: Boolean(assignmentId),
  });
}

export function useCreateKpiTreeMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: KpiTreeCreateRequest) => kpiApi.createTree(cycleId, req),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: kpiQueryKeys.trees(cycleId) }),
  });
}

export function useUpdateKpiTreeMutation(treeId: string, cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: KpiTreeUpdateRequest) => kpiApi.updateTree(treeId, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: kpiQueryKeys.trees(cycleId) });
      void qc.invalidateQueries({
        queryKey: kpiQueryKeys.treeDetail(treeId),
      });
    },
  });
}

export function useDeleteKpiTreeMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (treeId: string) => kpiApi.deleteTree(treeId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: kpiQueryKeys.trees(cycleId) }),
  });
}

export function useCreateKpiNodeMutation(treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: KpiNodeCreateRequest) => kpiApi.createNode(treeId, req),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) }),
  });
}

export function useUpdateKpiNodeMutation(treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { nodeId: string; req: KpiNodeUpdateRequest }) =>
      kpiApi.updateNode(vars.nodeId, vars.req),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) }),
  });
}

export function useDeleteKpiNodeMutation(treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (nodeId: string) => kpiApi.deleteNode(nodeId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) }),
  });
}

export function useCreateAssignmentMutation(nodeId: string, treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: KpiAssignmentCreateRequest) =>
      kpiApi.createAssignment(nodeId, req),
    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: kpiQueryKeys.nodeAssignments(nodeId),
      });
      void qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) });
    },
  });
}

export function useUpdateAssignmentMutation(nodeId: string, treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: {
      assignmentId: string;
      req: KpiAssignmentUpdateRequest;
    }) => kpiApi.updateAssignment(vars.assignmentId, vars.req),
    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: kpiQueryKeys.nodeAssignments(nodeId),
      });
      void qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) });
    },
  });
}

export function useDeleteAssignmentMutation(nodeId: string, treeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (assignmentId: string) =>
      kpiApi.deleteAssignment(assignmentId),
    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: kpiQueryKeys.nodeAssignments(nodeId),
      });
      void qc.invalidateQueries({ queryKey: kpiQueryKeys.treeDetail(treeId) });
    },
  });
}

export function useCreateActualMutation(assignmentId: string, cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: KpiActualCreateRequest) =>
      kpiApi.createActual(assignmentId, req),
    onSuccess: (_data, _vars, _ctx) => invalidateActualSiblings(qc, assignmentId, cycleId),
  });
}

export function useSupersedeActualMutation(
  assignmentId: string,
  cycleId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { actualId: string; req: KpiActualSupersedeRequest }) =>
      kpiApi.supersedeActual(vars.actualId, vars.req),
    onSuccess: () => invalidateActualSiblings(qc, assignmentId, cycleId),
  });
}

function invalidateActualSiblings(
  qc: ReturnType<typeof useQueryClient>,
  assignmentId: string,
  cycleId: string,
): void {
  void qc.invalidateQueries({
    queryKey: kpiQueryKeys.assignmentActuals(assignmentId),
  });
  // 최신 실적·달성률은 my-assignments 응답에도 반영되므로 cycle 범위 my 쿼리 무효화.
  void qc.invalidateQueries({ queryKey: kpiQueryKeys.all() });
  void cycleId; // cycleId 는 호출자 컨텍스트 보존용 (my key 가 객체라 부분 무효화는 all 로 처리)
}

// ---------- Helpers (표시 전용) ----------

/** 트리 노드 + 깊이 (parentId 로 flat → 계층 조립; 표시 순서). */
export interface KpiTreeNodeView {
  node: KpiNodeResponse;
  depth: number;
}

/**
 * flat nodes 배열을 부모-자식 들여쓰기 순서로 평탄화.
 * - root = parentId == null
 * - 자식은 부모 바로 아래, label 오름차순 안정 정렬.
 */
export function buildTreeNodeViews(
  nodes: KpiNodeResponse[],
): KpiTreeNodeView[] {
  const byParent = new Map<string | null, KpiNodeResponse[]>();
  for (const n of nodes) {
    const key = n.parentId ?? null;
    const bucket = byParent.get(key);
    if (bucket) bucket.push(n);
    else byParent.set(key, [n]);
  }
  for (const bucket of byParent.values()) {
    bucket.sort((a, b) => a.label.localeCompare(b.label));
  }

  const out: KpiTreeNodeView[] = [];
  const visit = (parentId: string | null, depth: number): void => {
    const children = byParent.get(parentId) ?? [];
    for (const child of children) {
      out.push({ node: child, depth });
      visit(child.id, depth + 1);
    }
  };
  visit(null, 0);

  // 고아(부모가 트리 내 없는 노드) 안전 처리 — out 에 빠진 노드는 root 취급으로 추가.
  if (out.length < nodes.length) {
    const seen = new Set(out.map((v) => v.node.id));
    for (const n of nodes) {
      if (!seen.has(n.id)) out.push({ node: n, depth: 0 });
    }
  }
  return out;
}

/** 달성률(0~1+) → 퍼센트 표시 문자열. null 이면 '—'. */
export function formatAchievementRate(rate: number | null): string {
  if (rate == null) return '—';
  return `${(rate * 100).toFixed(1)}%`;
}

/** weight(0~1) → 퍼센트 표시 문자열. null 이면 '—'. */
export function formatWeight(weight: number | null): string {
  if (weight == null) return '—';
  return `${(weight * 100).toFixed(1)}%`;
}
