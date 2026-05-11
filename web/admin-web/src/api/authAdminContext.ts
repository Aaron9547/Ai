import { http } from "@/plugins/http";

export interface AdminMembershipEntry {
  tenantId: number;
  tenantCode: string;
  role: string;
  tenantName: string;
}

export interface AdminContextLoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  memberships?: AdminMembershipEntry[];
}

export async function postAdminContextSwitch(body: {
  tenantId: number;
  role: string;
}): Promise<AdminContextLoginResponse> {
  const { data } = await http.post<AdminContextLoginResponse>("/api/v1/auth/admin-context", body);
  return data;
}
