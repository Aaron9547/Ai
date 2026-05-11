import { http } from "../plugins/http";

export type TenantStatus = "ACTIVE" | "DISABLED";

export type TenantRow = {
  id: number;
  code: string;
  name: string;
  status: TenantStatus | number;
  createdAt?: string;
  updatedAt?: string;
};

export async function listTenants(): Promise<TenantRow[]> {
  const { data } = await http.get<TenantRow[]>("/api/v1/admin/tenants");
  return data;
}

export async function createTenant(body: { code: string; name: string }): Promise<TenantRow> {
  const { data } = await http.post<TenantRow>("/api/v1/admin/tenants", body);
  return data;
}

export async function updateTenant(
  id: number,
  body: { name?: string; status?: TenantStatus },
): Promise<TenantRow> {
  const { data } = await http.put<TenantRow>(`/api/v1/admin/tenants/${id}`, body);
  return data;
}

export async function getTenantAdminMenus(tenantId: number): Promise<string[]> {
  const { data } = await http.get<string[]>(`/api/v1/admin/tenants/${tenantId}/admin-menus`);
  return data;
}

export async function putTenantAdminMenus(tenantId: number, menuCodes: string[]): Promise<void> {
  await http.put(`/api/v1/admin/tenants/${tenantId}/admin-menus`, { menuCodes });
}
