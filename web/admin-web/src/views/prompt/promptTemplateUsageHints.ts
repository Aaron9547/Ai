import { i18n } from "@/i18n";

/** 选中模板时右侧展示的用途说明（随界面语言切换） */
export function promptTemplateUsageHint(code: string): string | null {
  const normalized = code.trim().toLowerCase();
  const candidates = [normalized, normalized.replace(/_\d+$/, "")];
  for (const key of candidates) {
    if (!key) continue;
    const path = `views.promptTemplate.usage.${key}`;
    if (i18n.global.te(path)) {
      return String(i18n.global.t(path));
    }
  }
  return null;
}
