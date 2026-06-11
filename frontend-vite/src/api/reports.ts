/**
 * PerformanceReport 도메인 API — P0-S5 슬라이스 (append-only 리포트 발행 + 본인 결과 조회).
 *
 * 계약 SoT: `_workspace/00_input/p0_s5_contract.md` §6 (REST 7 endpoint + camelCase 응답 shape).
 *
 * BE 정합:
 * - base path: `/api/v1`
 * - my report: `/reports/my?cycleId=&employeeId=` (둘 다 필수, active 행만, 없으면 404 E9804449)
 * - content 는 **BE 가 발행 시점에 동결한 스냅샷** — FE 는 표시만 (재계산 금지, distribution 비율 % 환산 렌더 한정).
 * - publish = cycle 단위 일괄 (FINALIZED review 중 active report 미존재분만 생성) + supersede = 개별 재발행(신규 row + supersedesId).
 * - 게이트: publish/supersede 는 cycle.status==FINALIZED (E9804252) / view·acknowledge·supersede 는 active 행 (E9804934).
 *
 * 자매품 정합: calibration.ts / reviews.ts 패턴 (queryKeys / RQ 훅 / ADR-026 camelCase).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { ReviewKpiItemResponse } from './reviews';

// ---------- content 동결 스냅샷 shape (계약 §5 — BE 발행 시점 동결, 이후 불변) ----------

/** S~D 비율 분포 (발행 시점 전사 FINALIZED finalGrade 분포, 0~1). */
export type ReportDistribution = Partial<Record<'S' | 'A' | 'B' | 'C' | 'D', number>>;

export interface ReportContent {
  finalGrade: string | null;
  finalScore: number | null;
  kpiScore: number | null;
  mboScore: number | null; // P0 = null
  competencyScore: number | null; // P0 = null
  mraScore: number | null; // P0 = null
  managerComment: string | null; // selfComment 비포함 (화면 #7 사양)
  /** review.kpiScoreDetail 전체 사본 (이미 동결 스냅샷). */
  kpiItems: ReviewKpiItemResponse[] | null;
  /** 발행 시점 전사 분포 "비율" (건수 비노출 — E9). */
  distribution: ReportDistribution | null;
  nextAction: null; // P1 박제
}

// ---------- Response types (camelCase, 계약 §6) ----------

export interface ReportResponse {
  id: string;
  cycleId: string;
  reviewId: string;
  employeeId: string;
  publishedAt: string;
  publishedBy: string | null;
  content: ReportContent;
  viewedAt: string | null;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  supersedesId: string | null;
  /** computed — 다른 행이 나를 supersedesId 로 참조 (superseded 행 = 비-active). */
  superseded: boolean;
  createdAt: string;
}

export interface ReportPublishResponse {
  publishedCount: number;
  skippedCount: number;
  published: ReportResponse[];
}

// ---------- Request types (계약 §6) ----------

export interface ReportPublishRequest {
  actorEmployeeId?: string | null;
}

export interface ReportAcknowledgeRequest {
  actorEmployeeId?: string | null;
}

export interface ReportSupersedeRequest {
  actorEmployeeId?: string | null;
}

const BASE = '/api/v1';

// ---------- Query keys (ADR-026 / calibration.ts 정합) ----------

export const reportsQueryKeys = {
  all: () => buildQueryKey('performance', 'reports'),
  byCycle: (cycleId: string, employeeId?: string | null) =>
    buildQueryKey('performance', 'reports', 'cycle', cycleId, {
      employeeId: employeeId ?? null,
    }),
  detail: (reportId: string) =>
    buildQueryKey('performance', 'reports', 'detail', reportId),
  my: (cycleId: string, employeeId: string) =>
    buildQueryKey('performance', 'reports', 'my', { cycleId, employeeId }),
} as const;

// ---------- API ----------

export const reportsApi = {
  listByCycle: (cycleId: string, employeeId?: string | null) =>
    apiClient
      .get<ReportResponse[]>(`${BASE}/cycles/${cycleId}/reports`, {
        params: employeeId ? { employeeId } : undefined,
      })
      .then((r) => r.data),

  publish: (cycleId: string, req: ReportPublishRequest) =>
    apiClient
      .post<ReportPublishResponse>(
        `${BASE}/cycles/${cycleId}/reports/publish`,
        req,
      )
      .then((r) => r.data),

  get: (reportId: string) =>
    apiClient
      .get<ReportResponse>(`${BASE}/reports/${reportId}`)
      .then((r) => r.data),

  getMy: (cycleId: string, employeeId: string) =>
    apiClient
      .get<ReportResponse>(`${BASE}/reports/my`, {
        params: { cycleId, employeeId },
      })
      .then((r) => r.data),

  view: (reportId: string) =>
    apiClient
      .post<ReportResponse>(`${BASE}/reports/${reportId}/view`, {})
      .then((r) => r.data),

  acknowledge: (reportId: string, req: ReportAcknowledgeRequest) =>
    apiClient
      .post<ReportResponse>(`${BASE}/reports/${reportId}/acknowledge`, req)
      .then((r) => r.data),

  supersede: (reportId: string, req: ReportSupersedeRequest) =>
    apiClient
      .post<ReportResponse>(`${BASE}/reports/${reportId}/supersede`, req)
      .then((r) => r.data),
};

// ---------- RQ hooks ----------

export function useReportsByCycleQuery(
  cycleId: string | null | undefined,
  employeeId?: string | null,
) {
  return useQuery({
    queryKey: reportsQueryKeys.byCycle(cycleId ?? '', employeeId ?? null),
    queryFn: () =>
      reportsApi.listByCycle(cycleId as string, employeeId ?? null),
    enabled: Boolean(cycleId),
  });
}

export function useReportQuery(reportId: string | null | undefined) {
  return useQuery({
    queryKey: reportsQueryKeys.detail(reportId ?? ''),
    queryFn: () => reportsApi.get(reportId as string),
    enabled: Boolean(reportId),
  });
}

export function useMyReportQuery(
  cycleId: string | null | undefined,
  employeeId: string | null | undefined,
) {
  return useQuery({
    queryKey: reportsQueryKeys.my(cycleId ?? '', employeeId ?? ''),
    queryFn: () => reportsApi.getMy(cycleId as string, employeeId as string),
    enabled: Boolean(cycleId) && Boolean(employeeId),
    // report 없으면 404 (E9804449) — RQ 가 error 로 처리, 페이지에서 빈 상태 안내.
    retry: false,
  });
}

export function usePublishReportsMutation(cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReportPublishRequest) =>
      reportsApi.publish(cycleId, req),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: reportsQueryKeys.all() }),
  });
}

export function useViewReportMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reportId: string) => reportsApi.view(reportId),
    onSuccess: (data) => invalidateReport(qc, data),
  });
}

export function useAcknowledgeReportMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { reportId: string; req: ReportAcknowledgeRequest }) =>
      reportsApi.acknowledge(vars.reportId, vars.req),
    onSuccess: (data) => invalidateReport(qc, data),
  });
}

export function useSupersedeReportMutation(_cycleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { reportId: string; req: ReportSupersedeRequest }) =>
      reportsApi.supersede(vars.reportId, vars.req),
    // supersede 는 원본 행을 superseded 로 만들고 신규 active 행을 생성 → 목록 전체 갱신.
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: reportsQueryKeys.all() }),
  });
}

function invalidateReport(
  qc: ReturnType<typeof useQueryClient>,
  report: ReportResponse,
): void {
  // view/acknowledge 는 단일 행의 mutable 필드만 바꿈 — detail + my + cycle 목록 갱신.
  // reportsQueryKeys.all() 로 my(cycle,emp)·byCycle prefix 모두 포함.
  void qc.invalidateQueries({ queryKey: reportsQueryKeys.detail(report.id) });
  void qc.invalidateQueries({ queryKey: reportsQueryKeys.all() });
}

// ---------- 표시 헬퍼 (BE content 렌더 전용 — 재계산 금지, §5 주의) ----------

/** 비율(0~1) → 퍼센트 표시 문자열. null/undefined → '—'. */
export function formatReportRatio(ratio: number | null | undefined): string {
  if (ratio == null) return '—';
  return `${(ratio * 100).toFixed(0)}%`;
}

/** ISO timestamptz → 표시 문자열. null → '—'. */
export function formatReportDateTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}
