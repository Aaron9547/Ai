import { http } from "@/plugins/http";

export interface TenantRuntimeSettingRow {
  key: string;
  valueText: string;
  descriptionZh: string;
  valueKind: string;
}

export async function listTenantRuntimeSettings(): Promise<TenantRuntimeSettingRow[]> {
  const { data } = await http.get<TenantRuntimeSettingRow[]>("/api/v1/admin/tenant-runtime-settings");
  return data;
}

export async function replaceTenantRuntimeSettings(
  items: { key: string; valueText: string }[],
): Promise<void> {
  await http.put("/api/v1/admin/tenant-runtime-settings", { items });
}
