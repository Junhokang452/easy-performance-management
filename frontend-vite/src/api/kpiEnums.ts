/**
 * KPI 도메인 enum 타입 + 상수 — P0-S2 슬라이스.
 *
 * 계약 SoT: `_workspace/00_input/p0_s2_contract.md` §1 Entity 4건.
 * 별도 모듈로 분리 — kpi.ts 와 페이지/컴포넌트가 enum 만 가볍게 import.
 */

export type KpiTreeLevel = 'CORPORATE' | 'DIVISION' | 'TEAM' | 'INDIVIDUAL';

export type BscPerspective =
  | 'FINANCIAL'
  | 'CUSTOMER'
  | 'INTERNAL_PROCESS'
  | 'LEARNING_GROWTH';

export type KpiNodeSource = 'MANUAL' | 'HCM' | 'EXTERNAL';

export type KpiActualSource = 'MANUAL' | 'AUTO' | 'IMPORT';

export const ALL_KPI_TREE_LEVELS: KpiTreeLevel[] = [
  'CORPORATE',
  'DIVISION',
  'TEAM',
  'INDIVIDUAL',
];

export const ALL_BSC_PERSPECTIVES: BscPerspective[] = [
  'FINANCIAL',
  'CUSTOMER',
  'INTERNAL_PROCESS',
  'LEARNING_GROWTH',
];

/** P0 는 MANUAL 만 허용 (E9804239). HCM/EXTERNAL 은 P1 박제. */
export const ALL_KPI_NODE_SOURCES: KpiNodeSource[] = ['MANUAL', 'HCM', 'EXTERNAL'];
