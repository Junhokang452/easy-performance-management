/**
 * PerformanceReview 도메인 API — P0-S3 슬라이스 (자기/매니저 평가 + Self↔Manager 비교).
 *
 * 계약 SoT: `_workspace/00_input/p0_s3_contract.md` §6 (REST 11 endpoint + camelCase 응답 shape).
 *
 * BE 정합:
 * - base path: `/api/v1`
 * - my review: `/reviews/my?cycleId=&employeeId=` (둘 다 필수, 없으면 404 E9804447)
 * - 자동 점수 계산은 **BE 가 유일 계산자** — FE 는 표시만 (단 매니저 입력 중 가중 합산 프리뷰는 §5 산식으로 클라 계산, 표시용).
 * - 스냅샷: submit-manager 시 kpiScoreDetail 동결 + kpiScore 산출.
 * - 상태 전이: §3 매트릭스 4개만 (transition) + submit-self / submit-manager 전용 전이.
 *
 * 자매품 정합: kpi.ts / cycles.ts 패턴 (kpiQueryKeys / RQ 훅 / ADR-026 camelCase).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';

// ---------- enum / 상태 (계약 §1, §3) ----------

export type ReviewStatus =
  | 'DRAFT'
  | 'SELF_PENDING'
  | 'SELF_SUBMITTED'
  | 'MANAGER_PENDING'
  | 'MANAGER_SUBMITTED'
  | 'CALIBRATION'
  | 'FINALIZED'
  | 'APPEAL_REQUESTED'
  | 'APPEAL_RESOLVED'
  | 'ARCHIVED';

/** 10 상태 전체 (UiBadge·표시용). */
export const ALL_REVIEW_STATUSES: ReviewStatus[] = [
  'DRAFT',
  'SELF_PENDING',
  'SELF_SUBMITTED',
  'MANAGER_PENDING',
  'MANAGER_SUBMITTED',
  'CALIBRATION',
  'FINALIZED',
  'APPEAL_REQUESTED',
  'APPEAL_RESOLVED',
  'ARCHIVED',
];

// ---------- Response types (camelCase, 계약 §6) ----------

export interface ReviewKpiItemResponse {
  assignmentId: string;
  nodeLabel: string;
  treeName: string;
  weight: number | null; // effective
  target: number | null; // effective
  unit: string | null;
  latestActualValue: number | null;
  achievementRate: number | null;
  autoScore: number | null;
  managerScore: number | null;
  itemScore: number | null;
}

export interface ReviewResponse {
  id: string;
  cycleId: string;
  employeeId: string;
  status: ReviewStatus;
  kpiScore: number | null;
  mboScore: number | null;
  competencyScore: number | null;
  mraScore: number | null;
  finalScore: number | null;
  finalGrade: string | null;
  selfComment: string | null;
  managerComment: string | null;
  kpiScoreDetail: ReviewKpiItemResponse[] | null; // 저장 스냅샷 (submit-manager 전 null)
  finalizedAt: string | null;
  finalizedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewBulkCreateResponse {
  createdCount: number;
  skippedCount: number;
  created: ReviewResponse[];
}

// ---------- Request types (계약 §6) ----------

export interface ReviewCreateRequest {
  employeeId: string;
}

export interface ReviewBulkCreateRequest {
  employeeIds: string[];
}

/** PATCH itemScores 입력 (assignmentId 당 managerScore). */
export interface ReviewItemScoreInput {
  assignmentId: string;
  managerScore: number | null;
}

export interface ReviewUpdateRequest {
  selfComment?: string | null;
  managerComment?: string | null;
  itemScores?: ReviewItemScoreInput[];
}

export interface ReviewSubmitSelfRequest {
  selfComment?: string | null;
}

export interface ReviewSubmitManagerRequest {
  managerComment?: string | null;
  itemScores: ReviewItemScoreInput[];
}

export interface ReviewTransitionRequest {
  targetStatus: ReviewStatus;
  actorEmployeeId?: string | null;
}

const BASE = '/api/v1';

// ---------- Query keys (ADR-026 / kpi.ts 정합) ----------

export const reviewsQueryKeys = {
  all: () => buildQueryKey('performance', 'reviews'),
  byCycle: (cycleId: string, employeeId?: string | null) =>
    buildQueryKey('performance', 'reviews', 'cycle', cycleId, {
      employeeId: employeeId ?? null,
    }),
  detail: (reviewId: string) =>
    buildQueryKey('performance', 'reviews', 'detail', reviewId),
  my: (cycleId: string, employeeId: string) =>
    buildQueryKey('performance', 'reviews', 'my', { cycleId, employeeId }),
  kpiItems: (reviewId: string) =>
    buildQueryKey('performance', 'reviews', reviewId, 'kpi-items'),
} as const;

// ---------- API ----------

export const reviewsApi = {
  listByCycle: (cycleId: string, employeeId?: string | null) =>
    apiClient
      .get<ReviewResponse[]>(`${BASE}/cycles/${cycleId}/reviews`, {
        params: employeeId ? { employeeId } : undefined,
      })
      .then((r) => r.data),

  create: (cycleId: string, req: ReviewCreateRequest) =>
    apiClient
      .post<ReviewResponse>(`${BASE}/cycles/${cycleId}/reviews`, req)
      .then((r) => r.data),

  bulkCreate: (cycleId: string, req: ReviewBulkCreateRequest) =>
    apiClient
      .post<ReviewBulkCreateResponse>(
        `${BASE}/cycles/${cycleId}/reviews/bulk`,
        req,
      )
      .then((r) => r.data),

  get: (reviewId: string) =>
    apiClient
      .get<ReviewResponse>(`${BASE}/reviews/${reviewId}`)
      .then((r) => r.data),

  getMy: (cycleId: string, employeeId: string) =>
    apiClient
      .get<ReviewResponse>(`${BASE}/reviews/my`, {
        params: { cycleId, employeeId },
      })
      .then((r) => r.data),

  listKpiItems: (reviewId: string) =>
    apiClient
      .get<ReviewKpiItemResponse[]>(`${BASE}/reviews/${reviewId}/kpi-items`)
      .then((r) => r.data),

  update: (reviewId: string, req: ReviewUpdateRequest) =>
    apiClient
      .patch<ReviewResponse>(`${BASE}/reviews/${reviewId}`, req)
      .then((r) => r.data),

  submitSelf: (reviewId: string, req: ReviewSubmitSelfRequest) =>
    apiClient
      .post<ReviewResponse>(`${BASE}/reviews/${reviewId}/submit-self`, req)
      .then((r) => r.data),

  submitManager: (reviewId: string, req: ReviewSubmitManagerRequest) =>
    apiClient
      .post<ReviewResponse>(`${BASE}/reviews/${reviewId}/submit-manager`, req)
      .then((r) => r.data),

  transition: (reviewId: string, req: ReviewTransitionRequest) =>
    apiClient
      .post<ReviewResponse>(`${BASE}/reviews/${reviewId}/transition`, req)
      .then((r) => r.data),

  delete: (reviewId: string) =>
    apiClient
      .delete<void>(`${BASE}/reviews/${reviewId}`)
      .then((r) => r.data),
};

// ---------- RQ hooks ----------

export function useReviewsByCycleQuery(
  cycleId: string | null | undefined,
  employeeId?: string | null,
) {
  return useQuery({
    queryKey: reviewsQueryKeys.byCycle(cycleId ?? '', employeeId ?? null),
    queryFn: () =>
      reviewsApi.listByCycle(cycleId as string, employeeId ?? null),
    enabled: Boolean(cycleId),
  });
}

export function useReviewQuery(reviewId: string | null | undefined) {
  return useQuery({
    queryKey: reviewsQueryKeys.detail(reviewId ?? ''),
    queryFn: () => reviewsApi.get(reviewId as string),
    enabled: Boolean(reviewId),
  });
}

export function useMyReviewQuery(
  cycleId: string | null | undefined,
  employeeId: string | null | undefined,
) {
  return useQuery({
    queryKey: reviewsQueryKeys.my(cycleId ?? '', employeeId ?? ''),
    queryFn: () => reviewsApi.getMy(cycleId as string, employeeId as string),
    enabled: Boolean(cycleId) && Boolean(employeeId),
    // review 없으면 404 (E9804447) — RQ 가 error 로 처리, 페이지에서 빈 상태 안내.
    retry: false,
  });
}

export function useReviewKpiItemsQuery(reviewId: string | null | undefined) {
  return useQuery({
    queryKey: reviewsQueryKeys.kpiItems(reviewId ?? ''),
    queryFn: () => reviewsApi.listKpiItems(reviewId as string),
    enabled: Boolean(reviewId),
  });
}

export function useCreateReviewMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewCreateRequest) => reviewsApi.create(cycleId, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() }),
  });
}

export function useBulkCreateReviewMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewBulkCreateRequest) =>
      reviewsApi.bulkCreate(cycleId, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() }),
  });
}

export function useUpdateReviewMutation(reviewId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewUpdateRequest) =>
      reviewsApi.update(reviewId, req),
    onSuccess: () => invalidateReview(qc, reviewId),
  });
}

export function useSubmitSelfMutation(reviewId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewSubmitSelfRequest) =>
      reviewsApi.submitSelf(reviewId, req),
    onSuccess: () => invalidateReview(qc, reviewId),
  });
}

export function useSubmitManagerMutation(reviewId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewSubmitManagerRequest) =>
      reviewsApi.submitManager(reviewId, req),
    onSuccess: () => invalidateReview(qc, reviewId),
  });
}

export function useTransitionReviewMutation(reviewId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReviewTransitionRequest) =>
      reviewsApi.transition(reviewId, req),
    onSuccess: () => invalidateReview(qc, reviewId),
  });
}

export function useDeleteReviewMutation(_cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reviewId: string) => reviewsApi.delete(reviewId),
    onSuccess: () => qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() }),
  });
}

function invalidateReview(
  qc: ReturnType<typeof useQueryClient>,
  reviewId: string,
): void {
  // review 상태·점수·스냅샷이 바뀌면 detail / kpi-items / 모든 목록(my·cycle)을 갱신.
  void qc.invalidateQueries({ queryKey: reviewsQueryKeys.detail(reviewId) });
  void qc.invalidateQueries({ queryKey: reviewsQueryKeys.kpiItems(reviewId) });
  void qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() });
}

// ---------- 상태 전이 매트릭스 (계약 §3 — P0-S3 transition 4개) ----------

/**
 * `POST /reviews/{id}/transition` 로 가능한 전이 (§3 매트릭스 4개).
 * submit-self / submit-manager 는 전용 엔드포인트 — transition 메뉴에 미노출.
 */
export const REVIEW_TRANSITIONS: Partial<Record<ReviewStatus, ReviewStatus[]>> =
  {
    DRAFT: ['SELF_PENDING'],
    SELF_SUBMITTED: ['MANAGER_PENDING'],
    MANAGER_SUBMITTED: ['CALIBRATION'],
    CALIBRATION: ['FINALIZED'],
  };

export function getAllowedReviewTransitions(
  current: ReviewStatus,
): ReviewStatus[] {
  return REVIEW_TRANSITIONS[current] ?? [];
}

/** 각 전이의 필요 cycle.status (§3 — 안내 표시용). */
export const REVIEW_TRANSITION_REQUIRED_CYCLE: Partial<
  Record<ReviewStatus, string>
> = {
  SELF_PENDING: 'SELF_REVIEW',
  MANAGER_PENDING: 'MANAGER_REVIEW',
  CALIBRATION: 'CALIBRATION',
  FINALIZED: 'CALIBRATION',
};

// ---------- 점수 표시 / 가중 합산 프리뷰 (§5 산식 — FE 표시 전용) ----------

/** 점수(0~100) → 표시 문자열. null 이면 '—'. */
export function formatScore(score: number | null | undefined): string {
  if (score == null) return '—';
  return score.toFixed(2);
}

/** 달성률(0~1+) → 퍼센트 표시 문자열. null 이면 '—'. */
export function formatReviewAchievementRate(rate: number | null): string {
  if (rate == null) return '—';
  return `${(rate * 100).toFixed(1)}%`;
}

/** weight(0~1) → 퍼센트 표시 문자열. null 이면 '—'. */
export function formatReviewWeight(weight: number | null): string {
  if (weight == null) return '—';
  return `${(weight * 100).toFixed(1)}%`;
}

/**
 * 가중 합산 프리뷰 — **표시 전용** (BE submit-manager 의 kpiScore 가 SoT).
 *
 * 계약 §5 산식 동일:
 *   itemScore = managerScore ?? autoScore
 *   kpiScore  = Σ(itemScore × weight) / Σ(weight)   (itemScore 비-NULL 항목만)
 *   비-NULL 항목 0개 → null
 *
 * @param items   live 계산 kpi-items (effective weight 포함)
 * @param overrides assignmentId → 매니저 입력 점수 (입력 중인 폼 값). 비우면 item.managerScore 폴백.
 */
export function previewKpiScore(
  items: ReviewKpiItemResponse[],
  overrides: Record<string, number | null>,
): number | null {
  let weightedSum = 0;
  let weightTotal = 0;
  for (const item of items) {
    const weight = item.weight;
    if (weight == null || weight <= 0) continue;
    const override = overrides[item.assignmentId];
    const manager =
      override !== undefined ? override : item.managerScore;
    const itemScore = manager ?? item.autoScore;
    if (itemScore == null) continue;
    weightedSum += itemScore * weight;
    weightTotal += weight;
  }
  if (weightTotal <= 0) return null;
  return Math.round((weightedSum / weightTotal) * 100) / 100;
}
