import type { ComposerTranslation } from "vue-i18n";

const I18N_KEY_PREFIX = "common.settingValueKind";

/** 平台系统参数（sys_platform_setting）值类型筛选选项。 */
export const PLATFORM_SETTING_VALUE_KINDS = ["BOOLEAN", "INTEGER", "STRING"] as const;

/** 租户运行参数（ten_runtime_setting）值类型筛选选项。 */
export const TENANT_RUNTIME_SETTING_VALUE_KINDS = ["STRING", "BOOLEAN"] as const;

/** 将后端 valueKind 码值转为当前语言的类型名称；未知码值回退为原字符串。 */
export function formatSettingValueKindLabel(t: ComposerTranslation, kind: string | null | undefined): string {
  const code = (kind ?? "").trim().toUpperCase();
  if (!code) {
    return t("common.dash");
  }
  const key = `${I18N_KEY_PREFIX}.${code}`;
  const label = t(key);
  return label === key ? code : label;
}

/** 类型列 el-tag 配色。 */
export function settingValueKindTagType(kind: string): "success" | "warning" | "info" {
  const code = kind.trim().toUpperCase();
  if (code === "BOOLEAN") {
    return "success";
  }
  if (code === "INTEGER" || code === "INT") {
    return "warning";
  }
  return "info";
}
