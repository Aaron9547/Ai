import { http } from "../plugins/http";

export type UserRow = {
  id: number;
  /** 登录名（唯一） */
  loginName: string;
  /** 昵称/展示名 */
  displayName: string;
  status: "ACTIVE" | "DISABLED";
  /** 当前租户内角色；未加入租户时后端可能为 null */
  tenantRole?: TenantMemberRole | null;
  /** 近窗口内有 HTTP 访问日志，推断为在线 */
  sessionOnline: boolean;
  /** 北京时间，格式 yyyy-MM-dd HH:mm:ss */
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  lastLoginRegion: string | null;
};

export type TenantMemberRole = "FOUNDER" | "OWNER" | "ADMIN" | "MEMBER";

export type TenantMemberRow = {
  memberId: number;
  userId: number;
  loginName: string;
  displayName: string;
  userStatus: "ACTIVE" | "DISABLED";
  tenantId: number;
  role: TenantMemberRole;
  memberStatus: "ACTIVE" | "DISABLED";
  sessionOnline: boolean;
  /** 北京时间，格式 yyyy-MM-dd HH:mm:ss */
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  lastLoginRegion: string | null;
};

export async function listTenantMembers(params: {
  tenantId?: number;
  role?: TenantMemberRole | "";
  /** 仅所有者/创始人；默认 false */
  includeInactive?: boolean;
}) {
  const q: Record<string, string | number | boolean> = {};
  if (params.tenantId != null) q.tenantId = params.tenantId;
  if (params.role) q.role = params.role;
  if (params.includeInactive === true) q.includeInactive = true;
  const { data } = await http.get<TenantMemberRow[]>("/api/v1/admin/tenant-members", { params: q });
  return data;
}

export async function inviteTenantMember(body: {
  loginName: string;
  role: TenantMemberRole;
  tenantId?: number;
}) {
  const { data } = await http.post<TenantMemberRow>("/api/v1/admin/tenant-members", body);
  return data;
}

export async function removeTenantMember(userId: number, opts?: { tenantId?: number }) {
  const params: Record<string, number> = {};
  if (opts?.tenantId != null) params.tenantId = opts.tenantId;
  await http.delete(`/api/v1/admin/tenant-members/${userId}`, { params });
}

export async function listUsers(page = 1, size = 50) {
  const { data } = await http.get<UserRow[]>("/api/v1/admin/users", { params: { page, size } });
  return data;
}

export async function createUser(body: {
  loginName: string;
  password: string;
  displayName?: string;
  role?: TenantMemberRole;
}) {
  const { data } = await http.post<UserRow>("/api/v1/admin/users", body);
  return data;
}

export async function updateUser(
  id: number,
  body: { displayName?: string; password?: string; loginName?: string; status?: UserRow["status"] },
) {
  const { data } = await http.put<UserRow>(`/api/v1/admin/users/${id}`, body);
  return data;
}

export async function deleteUser(id: number) {
  await http.delete(`/api/v1/admin/users/${id}`);
}

export async function updateTenantMemberRole(
  id: number,
  role: TenantMemberRole,
  opts?: { tenantId?: number },
) {
  const body: { role: TenantMemberRole; tenantId?: number } = { role };
  if (opts?.tenantId != null) body.tenantId = opts.tenantId;
  const { data } = await http.put<UserRow>(`/api/v1/admin/users/${id}/tenant-role`, body);
  return data;
}

export async function kickUserSession(id: number): Promise<void> {
  await http.post(`/api/v1/admin/users/${id}/kick-session`);
}

export async function banUser(id: number): Promise<UserRow> {
  const { data } = await http.post<UserRow>(`/api/v1/admin/users/${id}/ban`);
  return data;
}
