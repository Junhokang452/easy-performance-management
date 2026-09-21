/** Browser-visible metadata only. Access and refresh credentials remain HttpOnly. */
export interface AuthSession {
  userId: string;
  tenantId: string;
  roles: string[];
}
export interface LoginRequest {
  email: string;
  password: string;
  tenantCode?: string;
}
