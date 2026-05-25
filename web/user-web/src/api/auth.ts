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

export interface RegisterOpenPayload {
  email: string;
  password: string;
  verificationCode: string;
  displayName?: string;
}

export interface SendRegisterCodeResponse {
  sent: boolean;
  cooldownSeconds: number;
}

export async function loginOpen(loginName: string, password: string): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>("/open/v1/auth/login", { loginName, password });
  return data;
}

export async function sendRegisterCode(email: string): Promise<SendRegisterCodeResponse> {
  const { data } = await http.post<SendRegisterCodeResponse>("/open/v1/auth/register/send-code", {
    email: email.trim().toLowerCase(),
  });
  return data;
}

export async function registerOpen(payload: RegisterOpenPayload): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>("/open/v1/auth/register", {
    email: payload.email.trim().toLowerCase(),
    password: payload.password,
    verificationCode: payload.verificationCode.trim(),
    displayName: payload.displayName?.trim() || undefined,
  });
  return data;
}
