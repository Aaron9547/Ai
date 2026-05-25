import { http } from "../plugins/http";
import { accountPathForUser, type AccountPrincipal } from "../utils/accountPrincipal";

export type UserRegistrationChannel = "ADMIN" | "EMAIL" | "PHONE" | "USERNAME" | "OAUTH";

export type UserRow = {
  account: AccountPrincipal;
  accountNo: string;
  loginName: string;
  email: string | null;
  phone: string | null;
  registrationChannel: UserRegistrationChannel;
  /** 北京时间 yyyy-MM-dd HH:mm:ss */
  registeredAt: string | null;
  displayName: string;
  status: "ACTIVE" | "DISABLED";
  tenantRole?: TenantMemberRole | null;
  sessionOnline: boolean;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  lastLoginRegion: string | null;
};

export type TenantMemberRole = "FOUNDER" | "OWNER" | "ADMIN" | "MEMBER";

export type TenantMemberRow = {
  memberId: number;
  account: AccountPrincipal;
  accountNo: string;
  loginName: string;
  email: string | null;
  phone: string | null;
  registrationChannel: UserRegistrationChannel;
  registeredAt: string | null;
  displayName: string;
  userStatus: "ACTIVE" | "DISABLED";
  tenantId: number;
  role: TenantMemberRole;
  memberStatus: "ACTIVE" | "DISABLED";
  sessionOnline: boolean;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  lastLoginRegion: string | null;
};

export async function listTenantMembers(params: {
  tenantId?: number;
  role?: TenantMemberRole | "";
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

export async function removeTenantMember(
  row: Pick<TenantMemberRow, "accountNo" | "loginName">,
  opts?: { tenantId?: number },
) {
  const params: Record<string, number> = {};
  if (opts?.tenantId != null) params.tenantId = opts.tenantId;
  await http.delete(`/api/v1/admin/tenant-members/${accountPathForUser(row)}`, { params });
}

export async function listUsers(page = 1, size = 50, opts?: { tenantId?: number }) {
  const params: Record<string, number> = { page, size };
  if (opts?.tenantId != null) params.tenantId = opts.tenantId;
  const { data } = await http.get<UserRow[]>("/api/v1/admin/users", { params });
  return data;
}

export async function createUser(body: {
  loginName: string;
  email?: string;
  phone?: string;
  password: string;
  displayName?: string;
  role?: TenantMemberRole;
}) {
  const { data } = await http.post<UserRow>("/api/v1/admin/users", body);
  return data;
}

export async function updateUser(
  row: Pick<UserRow, "accountNo" | "loginName">,
  body: {
    displayName?: string;
    password?: string;
    loginName?: string;
    email?: string | null;
    phone?: string | null;
    status?: UserRow["status"];
  },
) {
  const { data } = await http.put<UserRow>(`/api/v1/admin/users/${accountPathForUser(row)}`, body);
  return data;
}

export async function deleteUser(row: Pick<UserRow, "accountNo" | "loginName">) {
  await http.delete(`/api/v1/admin/users/${accountPathForUser(row)}`);
}

export async function updateTenantMemberRole(
  row: Pick<UserRow, "accountNo" | "loginName"> | Pick<TenantMemberRow, "accountNo" | "loginName">,
  role: TenantMemberRole,
  opts?: { tenantId?: number },
) {
  const body: { role: TenantMemberRole; tenantId?: number } = { role };
  if (opts?.tenantId != null) body.tenantId = opts.tenantId;
  const { data } = await http.put<UserRow>(
    `/api/v1/admin/users/${accountPathForUser(row)}/tenant-role`,
    body,
  );
  return data;
}

export async function kickUserSession(row: Pick<UserRow, "accountNo" | "loginName">): Promise<void> {
  await http.post(`/api/v1/admin/users/${accountPathForUser(row)}/kick-session`);
}

export async function banUser(row: Pick<UserRow, "accountNo" | "loginName">): Promise<UserRow> {
  const { data } = await http.post<UserRow>(`/api/v1/admin/users/${accountPathForUser(row)}/ban`);
  return data;
}
