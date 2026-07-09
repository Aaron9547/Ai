import http from "@/plugins/http";

export interface PlatformSettingRow {
  key: string;
  descriptionZh: string;
  valueKind: "BOOLEAN" | "INTEGER" | "STRING";
  valueText: string;
  defaultValueText: string;
}

export async function fetchPlatformSettings(): Promise<PlatformSettingRow[]> {
  const { data } = await http.get<PlatformSettingRow[]>("/api/v1/admin/platform-settings");
  return data ?? [];
}

export async function replacePlatformSettings(
  items: Array<{ key: string; valueText: string }>,
): Promise<void> {
  await http.put("/api/v1/admin/platform-settings", { items });
}
