import { http } from "@/plugins/http";
import type { MybatisPage } from "@/types/page";

export interface TenantRuntimeSettingRow {
  key: string;
  valueText: string;
  descriptionZh: string;
  valueKind: string;
  /** 管理端列表是否默认遮罩展示（密钥等）；valueText 仍为明文 */
  sensitive?: boolean;
}

export async function fetchTenantRuntimeSettingsPage(params: {
  current?: number;
  size?: number;
  keyword?: string;
  /** STRING | BOOLEAN；空表示不过滤 */
  valueKind?: string;
}): Promise<MybatisPage<TenantRuntimeSettingRow>> {
  const kw = params.keyword?.trim();
  const vk = params.valueKind?.trim();
  const { data } = await http.get<MybatisPage<TenantRuntimeSettingRow>>("/api/v1/admin/tenant-runtime-settings", {
    params: {
      current: params.current ?? 1,
      size: params.size ?? 10,
      ...(kw ? { keyword: kw } : {}),
      ...(vk ? { valueKind: vk } : {}),
    },
  });
  return data;
}

export async function replaceTenantRuntimeSettings(
  items: { key: string; valueText: string }[],
): Promise<void> {
  await http.put("/api/v1/admin/tenant-runtime-settings", { items });
}
