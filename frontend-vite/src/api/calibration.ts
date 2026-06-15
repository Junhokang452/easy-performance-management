/**
 * CalibrationSession + RatingDistribution API — P0-S4 슬라이스
 * (캘리브레이션 세션 + 강제 분포 시뮬레이터).
 *
 * 계약 SoT: `_workspace/00_input/p0_s4_contract.md` §6 (REST 11 endpoint + camelCase 응답 shape).
 *
 * BE 정합:
 * - base path: `/api/v1`
 * - 등급·분포 계산은 **BE 가 유일 계산자** — FE 는 표시만 (DistributionBars 의 % 환산 렌더만 허용).
 * - 분포 객체 키 단위: targetDistribution = 비율 (0~1 number) / current·resulting·actual = 건수 (int).
 * - 상태 전이: §3 매트릭스 — transition = PLANNED→IN_SESSION + CONFIRMED→CLOSED 만
 *   (CONFIRMED 진입은 confirm endpoint 전용 / ADJUSTED 는 adjustments 첫 호출 시 자동 승격).
 *
 * 자매품 정합: reviews.ts / cycles.ts 패턴 (queryKeys / RQ 훅 / ADR-026 camelCase).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import { reviewsQueryKeys } from './reviews';

// ---------- enum / 상태 (계약 §1, §3) ----------

export type CalibrationStatus =
  | 'PLANNED'
  | 'IN_SESSION'
  | 'ADJUSTED'
  | 'CONFIRMED'
  | 'CLOSED';

/** 5 상태 전체 (UiBadge·표시용). */
export const ALL_CALIBRATION_STATUSES: CalibrationStatus[] = [
  'PLANNED',
  'IN_SESSION',
  'ADJUSTED',
  'CONFIRMED',
  'CLOSED',
];

/** S~D + UNRATED 등급 버킷 (분포·UiBadge). */
export type GradeBucket = 'S' | 'A' | 'B' | 'C' | 'D' | 'UNRATED';

/** 조정 가능 등급 (S~D — UNRATED 는 조정 대상 아님). */
export const ADJUSTABLE_GRADES: Array<'S' | 'A' | 'B' | 'C' | 'D'> = [
  'S',
  'A',
  'B',
  'C',
  'D',
];

/** 분포 버킷 표시 순서 (S→A→B→C→D→UNRATED). */
export const GRADE_BUCKETS: GradeBucket[] = ['S', 'A', 'B', 'C', 'D', 'UNRATED'];

// ---------- Response types (camelCase, 계약 §6) ----------

export interface AdjustmentEntry {
  at: string;
  actorEmployeeId: string | null;
  reviewId: string;
  employeeId: string;
  fromGrade: string | null;
  toGrade: string;
  reason: string | null;
}

export interface CalibrationSessionResponse {
  id: string;
  cycleId: string;
  ownerOrgUnitId: string | null;
  status: CalibrationStatus;
  scheduledAt: string | null;
  participantIds: string[] | null;
  adjustmentLog: AdjustmentEntry[] | null;
  confirmedAt: string | null;
  confirmedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CalibrationConfirmResponse {
  session: CalibrationSessionResponse;
  finalizedCount: number;
  skippedCount: number;
}

/** S~D 비율 분포 (target — 0~1). */
export type GradeRatioMap = Partial<Record<'S' | 'A' | 'B' | 'C' | 'D', number>>;

/** S~D 건수 분포 (resulting — int). */
export type GradeCountMap = Partial<Record<'S' | 'A' | 'B' | 'C' | 'D', number>>;

/** S~D + UNRATED 건수 분포 (current — int). */
export type GradeCountWithUnratedMap = Partial<Record<GradeBucket, number>>;

export interface SimulationEntry {
  at: string;
  actorEmployeeId: string | null;
  targetDistribution: GradeRatioMap;
  appliedCount: number;
  skippedCount: number;
  resultingDistribution: GradeCountMap;
}

export interface DistributionResponse {
  cycleId: string;
  distributionMode: string;
  ratingScale: string;
  targetDistribution: GradeRatioMap | null;
  currentDistribution: GradeCountWithUnratedMap;
  totalReviews: number;
  calibrationReadyCount: number;
  forcedApplied: boolean;
  appliedAt: string | null;
  appliedBy: string | null;
  simulationLog: SimulationEntry[] | null;
}

export interface ProposedGradeRow {
  reviewId: string;
  employeeId: string;
  kpiScore: number | null;
  currentGrade: string; // effectiveGrade
  proposedGrade: string;
}

export interface DistributionSimulationResponse {
  proposed: ProposedGradeRow[];
  resultingDistribution: GradeCountMap;
  targetDistribution: GradeRatioMap;
}

export interface DistributionApplyResponse {
  appliedCount: number;
  skippedCount: number;
  resultingDistribution: GradeCountMap;
}

// ---------- Request types (계약 §6) ----------

export interface CalibrationSessionCreateRequest {
  ownerOrgUnitId?: string | null;
  scheduledAt?: string | null;
  participantIds?: string[] | null;
}

export interface CalibrationSessionUpdateRequest {
  ownerOrgUnitId?: string | null;
  scheduledAt?: string | null;
  participantIds?: string[] | null;
}

export interface CalibrationTransitionRequest {
  targetStatus: CalibrationStatus;
  actorEmployeeId?: string | null;
}

export interface CalibrationAdjustmentRequest {
  reviewId: string;
  toGrade: string;
  reason?: string | null;
  actorEmployeeId?: string | null;
}

export interface CalibrationConfirmRequest {
  actorEmployeeId?: string | null;
  finalizeReviews?: boolean;
}

export interface DistributionSimulateRequest {
  targetDistribution?: GradeRatioMap | null;
}

export interface DistributionApplyRequest {
  targetDistribution?: GradeRatioMap | null;
  actorEmployeeId?: string | null;
}

const BASE = '/api/v1';

// ---------- Query keys (ADR-026 / reviews.ts 정합) ----------

export const calibrationQueryKeys = {
  all: () => buildQueryKey('performance', 'calibration'),
  sessionsByCycle: (cycleId: string) =>
    buildQueryKey('performance', 'calibration', 'sessions', 'cycle', cycleId),
  sessionDetail: (sessionId: string) =>
    buildQueryKey('performance', 'calibration', 'sessions', 'detail', sessionId),
  distribution: (cycleId: string) =>
    buildQueryKey('performance', 'calibration', 'distribution', cycleId),
} as const;

// ---------- API ----------

export const calibrationApi = {
  listSessions: (cycleId: string) =>
    apiClient
      .get<CalibrationSessionResponse[]>(
        `${BASE}/cycles/${cycleId}/calibration-sessions`,
      )
      .then((r) => r.data),

  createSession: (cycleId: string, req: CalibrationSessionCreateRequest) =>
    apiClient
      .post<CalibrationSessionResponse>(
        `${BASE}/cycles/${cycleId}/calibration-sessions`,
        req,
      )
      .then((r) => r.data),

  getSession: (sessionId: string) =>
    apiClient
      .get<CalibrationSessionResponse>(
        `${BASE}/calibration-sessions/${sessionId}`,
      )
      .then((r) => r.data),

  updateSession: (sessionId: string, req: CalibrationSessionUpdateRequest) =>
    apiClient
      .patch<CalibrationSessionResponse>(
        `${BASE}/calibration-sessions/${sessionId}`,
        req,
      )
      .then((r) => r.data),

  transitionSession: (sessionId: string, req: CalibrationTransitionRequest) =>
    apiClient
      .post<CalibrationSessionResponse>(
        `${BASE}/calibration-sessions/${sessionId}/transition`,
        req,
      )
      .then((r) => r.data),

  adjust: (sessionId: string, req: CalibrationAdjustmentRequest) =>
    apiClient
      .post<CalibrationSessionResponse>(
        `${BASE}/calibration-sessions/${sessionId}/adjustments`,
        req,
      )
      .then((r) => r.data),

  confirmSession: (sessionId: string, req: CalibrationConfirmRequest) =>
    apiClient
      .post<CalibrationConfirmResponse>(
        `${BASE}/calibration-sessions/${sessionId}/confirm`,
        req,
      )
      .then((r) => r.data),

  deleteSession: (sessionId: string) =>
    apiClient
      .delete<void>(`${BASE}/calibration-sessions/${sessionId}`)
      .then((r) => r.data),

  getDistribution: (cycleId: string) =>
    apiClient
      .get<DistributionResponse>(`${BASE}/cycles/${cycleId}/distribution`)
      .then((r) => r.data),

  simulate: (cycleId: string, req: DistributionSimulateRequest) =>
    apiClient
      .post<DistributionSimulationResponse>(
        `${BASE}/cycles/${cycleId}/distribution/simulate`,
        req,
      )
      .then((r) => r.data),

  apply: (cycleId: string, req: DistributionApplyRequest) =>
    apiClient
      .post<DistributionApplyResponse>(
        `${BASE}/cycles/${cycleId}/distribution/apply`,
        req,
      )
      .then((r) => r.data),
};

// ---------- RQ hooks ----------

export function useCalibrationSessionsQuery(cycleId: string | null | undefined) {
  return useQuery({
    queryKey: calibrationQueryKeys.sessionsByCycle(cycleId ?? ''),
    queryFn: () => calibrationApi.listSessions(cycleId as string),
    enabled: Boolean(cycleId),
  });
}

export function useCalibrationSessionQuery(
  sessionId: string | null | undefined,
) {
  return useQuery({
    queryKey: calibrationQueryKeys.sessionDetail(sessionId ?? ''),
    queryFn: () => calibrationApi.getSession(sessionId as string),
    enabled: Boolean(sessionId),
  });
}

export function useDistributionQuery(cycleId: string | null | undefined) {
  return useQuery({
    queryKey: calibrationQueryKeys.distribution(cycleId ?? ''),
    queryFn: () => calibrationApi.getDistribution(cycleId as string),
    enabled: Boolean(cycleId),
  });
}

export function useCreateCalibrationSessionMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CalibrationSessionCreateRequest) =>
      calibrationApi.createSession(cycleId, req),
    onSuccess: () =>
      void qc.invalidateQueries({
        queryKey: calibrationQueryKeys.sessionsByCycle(cycleId),
      }),
  });
}

export function useUpdateCalibrationSessionMutation(
  cycleId: string,
  sessionId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CalibrationSessionUpdateRequest) =>
      calibrationApi.updateSession(sessionId, req),
    onSuccess: () => invalidateSession(qc, cycleId, sessionId),
  });
}

export function useTransitionCalibrationSessionMutation(
  cycleId: string,
  sessionId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CalibrationTransitionRequest) =>
      calibrationApi.transitionSession(sessionId, req),
    onSuccess: () => invalidateSession(qc, cycleId, sessionId),
  });
}

export function useAdjustGradeMutation(cycleId: string, sessionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CalibrationAdjustmentRequest) =>
      calibrationApi.adjust(sessionId, req),
    onSuccess: () => {
      // 등급 조정은 session adjustment_log + review.finalGrade + 분포에 영향.
      invalidateSession(qc, cycleId, sessionId);
      void qc.invalidateQueries({
        queryKey: calibrationQueryKeys.distribution(cycleId),
      });
      void qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() });
    },
  });
}

export function useConfirmCalibrationSessionMutation(
  cycleId: string,
  sessionId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CalibrationConfirmRequest) =>
      calibrationApi.confirmSession(sessionId, req),
    onSuccess: () => {
      // finalizeReviews 옵션이 review 상태를 FINALIZED 로 일괄 전이할 수 있음.
      invalidateSession(qc, cycleId, sessionId);
      void qc.invalidateQueries({
        queryKey: calibrationQueryKeys.distribution(cycleId),
      });
      void qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() });
    },
  });
}

export function useDeleteCalibrationSessionMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => calibrationApi.deleteSession(sessionId),
    onSuccess: () =>
      void qc.invalidateQueries({
        queryKey: calibrationQueryKeys.sessionsByCycle(cycleId),
      }),
  });
}

export function useSimulateDistributionMutation(cycleId: string) {
  // 무저장 순수 계산 (계약 §0-1) — invalidate 불요.
  return useMutation({
    mutationFn: (req: DistributionSimulateRequest) =>
      calibrationApi.simulate(cycleId, req),
  });
}

export function useApplyDistributionMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: DistributionApplyRequest) =>
      calibrationApi.apply(cycleId, req),
    onSuccess: () => {
      // 강제 적용은 review.finalGrade 일괄 변경 + RatingDistribution upsert.
      void qc.invalidateQueries({
        queryKey: calibrationQueryKeys.distribution(cycleId),
      });
      void qc.invalidateQueries({ queryKey: reviewsQueryKeys.all() });
    },
  });
}

function invalidateSession(
  qc: ReturnType<typeof useQueryClient>,
  cycleId: string,
  sessionId: string,
): void {
  void qc.invalidateQueries({
    queryKey: calibrationQueryKeys.sessionDetail(sessionId),
  });
  void qc.invalidateQueries({
    queryKey: calibrationQueryKeys.sessionsByCycle(cycleId),
  });
}

// ---------- 상태 전이 매트릭스 (계약 §3 — transition endpoint 한정) ----------

/**
 * `POST /calibration-sessions/{id}/transition` 로 가능한 전이 (§3 매트릭스).
 * - PLANNED → IN_SESSION (회의 시작 — CALIBRATION 단계 한정)
 * - CONFIRMED → CLOSED (종결)
 *
 * IN_SESSION/ADJUSTED → CONFIRMED 는 confirm endpoint 전용 (transition 으로 불가, E9804246).
 * IN_SESSION → ADJUSTED 는 adjustments 첫 호출 시 서비스 자동 승격 (메뉴 비노출).
 */
export const CALIBRATION_TRANSITIONS: Partial<
  Record<CalibrationStatus, CalibrationStatus[]>
> = {
  PLANNED: ['IN_SESSION'],
  CONFIRMED: ['CLOSED'],
};

export function getAllowedCalibrationTransitions(
  current: CalibrationStatus,
): CalibrationStatus[] {
  return CALIBRATION_TRANSITIONS[current] ?? [];
}

/** confirm endpoint 로 CONFIRMED 진입 가능한 상태 (§3). */
export function canConfirmSession(status: CalibrationStatus): boolean {
  return status === 'IN_SESSION' || status === 'ADJUSTED';
}

/** 등급 조정(adjustments) 가능 상태 — IN_SESSION / ADJUSTED (§3, CALIBRATION 단계 전제). */
export function canAdjustSession(status: CalibrationStatus): boolean {
  return status === 'IN_SESSION' || status === 'ADJUSTED';
}

// ---------- 표시 헬퍼 (BE 응답 렌더 전용 — 재계산 금지, §7 주의) ----------

/** 비율(0~1) → 퍼센트 표시 문자열. null/undefined → '—'. */
export function formatRatio(ratio: number | null | undefined): string {
  if (ratio == null) return '—';
  return `${(ratio * 100).toFixed(0)}%`;
}

/** 분포 맵의 총 건수 합 (DistributionBars 의 % 환산 렌더용 — 표시 전용). */
export function distributionTotal(
  dist: Partial<Record<string, number>> | null | undefined,
): number {
  if (!dist) return 0;
  return Object.values(dist).reduce<number>((acc, v) => acc + (v ?? 0), 0);
}

/**
 * 유효 등급 표시값 (effectiveGrade) — `finalGrade ?? band(kpiScore)`.
 *
 * 계약 §5 산식 = `review.finalGrade ?? bandGrade(review.kpiScore)`.
 * 밴드 = P0-S3 밴드 (S≥90/A≥80/B≥70/C≥60/D<60, kpiScore NULL → UNRATED).
 *
 * **표시 전용** — 본부 calibration grid 의 행 등급 라벨 렌더에만 사용.
 * adjust/apply 의 결과 등급은 BE 가 finalGrade 로 반영하므로 그 경우 finalGrade 가 우선됨.
 * (분포·시뮬레이션 등급 계산은 BE 가 SoT — 본 함수는 reviews 목록의 행 표시만 보조.)
 */
export function effectiveGradeLabel(
  finalGrade: string | null,
  kpiScore: number | null,
): GradeBucket {
  if (finalGrade != null && finalGrade !== '') {
    return (finalGrade as GradeBucket) ?? 'UNRATED';
  }
  if (kpiScore == null) return 'UNRATED';
  if (kpiScore >= 90) return 'S';
  if (kpiScore >= 80) return 'A';
  if (kpiScore >= 70) return 'B';
  if (kpiScore >= 60) return 'C';
  return 'D';
}
