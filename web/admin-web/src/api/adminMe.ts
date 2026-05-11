import { http } from "@/plugins/http";

export interface AdminMeMembership {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  role: string;
}

export interface AdminMeView {
  loginName: string;
  /** 昵称/展示名；空时前端用 loginName（与 JWT sub 一致） */
  displayName: string;
  tenantId: number;
  memberRole: string | null;
  memberships: AdminMeMembership[];
  allowedMenuCodes: string[];
  adminPortal: boolean;
}

export async function fetchAdminMe(): Promise<AdminMeView> {
  const { data } = await http.get<AdminMeView>("/api/v1/admin/me");
  return data;
}
