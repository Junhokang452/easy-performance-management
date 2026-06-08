/**
 * Auth 타입 — 단계 3 BE-CC-2 JWT 5분리 정합 (미진입 stub).
 *
 * BE `AuthDtos.TokenResponse` 와 1:1 wire 매핑 예정 (단계 3 진입 시).
 * 현재는 dev stub login으로 무인증 navigation 가능 (BE security minimal).
 */
export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  accessExpiresInSec: number;
  refreshToken: string;
  refreshExpiresInSec: number;
  userId: string;
  tenantId: string;
  roles: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthSession {
  userId: string;
  tenantId: string;
  roles: string[];
  accessToken: string;
  refreshToken: string;
  /** Access token expiry epoch ms — silent refresh 트리거 기준 */
  accessExpiresAt: number;
}
