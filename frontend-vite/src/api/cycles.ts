/**
 * EvaluationCycle + EvaluationPolicy API — P0-S1 슬라이스 FE 진입 (2026-06-11).
 *
 * SoT: `_workspace/evaluation_research_2026-06-11/decisions_2026-06-11.md`
 *
 * BE 정합:
 * - base path: `/api/v1/cycles`
 * - list: `Page<CycleResponse>` envelope (Spring Data)
 * - status transition: POST `/{id}/transition` body `{ toStatus }`
 * - policy: GET/PUT `/{id}/policy`
 *
 * 상태 전이 매트릭스 (FE 표시용):
 *   PLANNED → ACTIVE | CANCELLED
 *   ACTIVE → GOAL_SETTING | CANCELLED
 *   GOAL_SETTING → MID_REVIEW | CANCELLED
 *   MID_REVIEW → SELF_REVIEW | CANCELLED
 *   SELF_REVIEW → MANAGER_REVIEW | CANCELLED
 *   MANAGER_REVIEW → CALIBRATION | CANCELLED
 *   CALIBRATION → FINALIZED | CANCELLED
 *   FINALIZED → (terminal)
 *   CANCELLED → (terminal)
 *
 * 자매품 정합: selfEvaluation.ts / personalOkr.ts 패턴.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { PageEnvelope } from './selfEvaluation';

export type CycleStatus =
  | 'PLANNED'
  | 'ACTIVE'
  | 'GOAL_SETTING'
  | 'MID_REVIEW'
  | 'SELF_REVIEW'
  | 'MANAGER_REVIEW'
  | 'CALIBRATION'
  | 'FINALIZED'
  | 'CANCELLED';

export type CycleType =
  | 'HALF_ANNUAL'
  | 'ANNUAL'
  | 'QUARTERLY'
  | 'MONTHLY'
  | 'CUSTOM';

export type DistributionMode = 'HYBRID' | 'FORCED' | 'ABSOLUTE';
export type RatingScale = 'S_A_B_C_D' | 'ONE_TO_FIVE' | 'ONE_TO_HUNDRED';

export interface CycleResponse {
  id: string;
  tenantId: string;
  name: string;
  periodStart: string;
  periodEnd: string;
  cycleType: CycleType;
  status: CycleStatus;
  policyId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyResponse {
  id: string;
  cycleId: string;
  distributionMode: DistributionMode;
  ratingScale: RatingScale;
  appealEnabled: boolean;
  bscEnabled: boolean;
  achievementLogCutoffDays: number;
  forcedDistribution: Record<string, number> | null;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyUpsertRequest {
  distributionMode: DistributionMode;
  ratingScale: RatingScale;
  appealEnabled: boolean;
  bscEnabled: boolean;
  achievementLogCutoffDays: number;
  forcedDistribution: Record<string, number> | null;
}

export interface CycleCreateRequest {
  name: string;
  periodStart: string;
  periodEnd: string;
  cycleType: CycleType;
  policy?: PolicyUpsertRequest;
}

export interface CycleUpdateRequest {
  name?: string;
  periodStart?: string;
  periodEnd?: string;
  cycleType?: CycleType;
}

export interface CycleStatusTransitionRequest {
  toStatus: CycleStatus;
}

const BASE = '/api/v1/cycles';

export const cyclesQueryKeys = {
  all: () => buildQueryKey('performance', 'cycles'),
  list: () => buildQueryKey('performance', 'cycles', 'list'),
  detail: (id: string) => buildQueryKey('performance', 'cycles', 'detail', id),
  policy: (cycleId: string) =>
    buildQueryKey('performance', 'cycles', 'policy', cycleId),
} as const;

export const cyclesApi = {
  list: () =>
    apiClient.get<PageEnvelope<CycleResponse>>(BASE).then((r) => r.data),

  get: (id: string) =>
    apiClient.get<CycleResponse>(`${BASE}/${id}`).then((r) => r.data),

  create: (req: CycleCreateRequest) =>
    apiClient.post<CycleResponse>(BASE, req).then((r) => r.data),

  update: (id: string, req: CycleUpdateRequest) =>
    apiClient.patch<CycleResponse>(`${BASE}/${id}`, req).then((r) => r.data),

  transition: (id: string, req: CycleStatusTransitionRequest) =>
    apiClient
      .post<CycleResponse>(`${BASE}/${id}/transition`, req)
      .then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),

  getPolicy: (cycleId: string) =>
    apiClient.get<PolicyResponse>(`${BASE}/${cycleId}/policy`).then((r) => r.data),

  upsertPolicy: (cycleId: string, req: PolicyUpsertRequest) =>
    apiClient
      .put<PolicyResponse>(`${BASE}/${cycleId}/policy`, req)
      .then((r) => r.data),
};

// ---------- RQ hooks ----------

export function useCyclesQuery() {
  return useQuery({
    queryKey: cyclesQueryKeys.list(),
    queryFn: () => cyclesApi.list(),
  });
}

export function useCycleQuery(id: string | null | undefined) {
  return useQuery({
    queryKey: cyclesQueryKeys.detail(id ?? ''),
    queryFn: () => cyclesApi.get(id as string),
    enabled: Boolean(id),
  });
}

export function usePolicyQuery(cycleId: string | null | undefined) {
  return useQuery({
    queryKey: cyclesQueryKeys.policy(cycleId ?? ''),
    queryFn: () => cyclesApi.getPolicy(cycleId as string),
    enabled: Boolean(cycleId),
  });
}

export function useCreateCycleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CycleCreateRequest) => cyclesApi.create(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: cyclesQueryKeys.all() }),
  });
}

export function useUpdateCycleMutation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CycleUpdateRequest) => cyclesApi.update(id, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.all() });
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.detail(id) });
    },
  });
}

export function useTransitionCycleMutation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CycleStatusTransitionRequest) =>
      cyclesApi.transition(id, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.all() });
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.detail(id) });
    },
  });
}

export function useDeleteCycleMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cyclesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: cyclesQueryKeys.all() }),
  });
}

export function useUpsertPolicyMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PolicyUpsertRequest) =>
      cyclesApi.upsertPolicy(cycleId, req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.policy(cycleId) });
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.detail(cycleId) });
      void qc.invalidateQueries({ queryKey: cyclesQueryKeys.all() });
    },
  });
}

// ---------- Helpers ----------

/** 상태 전이 매트릭스 — 현재 상태에서 가능한 다음 상태. */
export const STATUS_TRANSITIONS: Record<CycleStatus, CycleStatus[]> = {
  PLANNED: ['ACTIVE', 'CANCELLED'],
  ACTIVE: ['GOAL_SETTING', 'CANCELLED'],
  GOAL_SETTING: ['MID_REVIEW', 'CANCELLED'],
  MID_REVIEW: ['SELF_REVIEW', 'CANCELLED'],
  SELF_REVIEW: ['MANAGER_REVIEW', 'CANCELLED'],
  MANAGER_REVIEW: ['CALIBRATION', 'CANCELLED'],
  CALIBRATION: ['FINALIZED', 'CANCELLED'],
  FINALIZED: [],
  CANCELLED: [],
};

export function getAllowedNextStatuses(current: CycleStatus): CycleStatus[] {
  return STATUS_TRANSITIONS[current] ?? [];
}

export const ALL_CYCLE_TYPES: CycleType[] = [
  'HALF_ANNUAL',
  'ANNUAL',
  'QUARTERLY',
  'MONTHLY',
  'CUSTOM',
];

export const ALL_DISTRIBUTION_MODES: DistributionMode[] = [
  'HYBRID',
  'FORCED',
  'ABSOLUTE',
];

export const ALL_RATING_SCALES: RatingScale[] = [
  'S_A_B_C_D',
  'ONE_TO_FIVE',
  'ONE_TO_HUNDRED',
];

/** S_A_B_C_D 분포 키 (forcedDistribution 입력 폼). */
export const SABCD_KEYS = ['S', 'A', 'B', 'C', 'D'] as const;

/** 분포 합 검증 — tolerance 0.001. */
export function isDistributionSumValid(
  dist: Record<string, number> | null,
): boolean {
  if (!dist) return false;
  const sum = Object.values(dist).reduce((acc, v) => acc + (v ?? 0), 0);
  return Math.abs(sum - 1.0) < 0.001;
}
