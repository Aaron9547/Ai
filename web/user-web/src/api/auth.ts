import { http } from "../plugins/http";

export type TenantMemberRole = "FOUNDER" | "OWNER" | "ADMIN" | "MEMBER";

export interface LoginMembership {
  tenantId: number;
  tenantCode: string;
  tenantName?: string;
  role: TenantMemberRole;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  memberships: LoginMembership[];
}

export async function loginOpen(loginName: string, password: string): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>("/open/v1/auth/login", { loginName, password });
  return data;
}

export async function registerOpen(
  loginName: string,
  password: string,
  displayName?: string,
): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>("/open/v1/auth/register", {
    loginName,
    password,
    displayName: displayName?.trim() || undefined,
  });
  return data;
}
