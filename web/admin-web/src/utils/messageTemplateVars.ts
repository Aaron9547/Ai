import type { MessageSceneCode } from "@/api/message";

/** 与后端 {@code MessageTemplateSupport} 一致：单花括号占位符。 */
export function templatePlaceholder(key: string): string {
  return `{${key}}`;
}

export type MessageTemplateVarDef = {
  key: string;
  /** vue-i18n 键，如 admin.message.varTenantName */
  labelKey: string;
};

export const MESSAGE_SCENE_TEMPLATE_VARS: Record<MessageSceneCode, MessageTemplateVarDef[]> = {
  REGISTER_VERIFICATION: [
    { key: "tenantName", labelKey: "admin.message.varTenantName" },
    { key: "code", labelKey: "admin.message.varCode" },
    { key: "ttlMinutes", labelKey: "admin.message.varTtlMinutes" },
  ],
  KNOWLEDGE_PLANET_WEEKLY: [
    { key: "tenantName", labelKey: "admin.message.varTenantName" },
    { key: "userName", labelKey: "admin.message.varUserName" },
    { key: "email", labelKey: "admin.message.varEmail" },
    { key: "weekLabel", labelKey: "admin.message.varWeekLabel" },
    { key: "summary", labelKey: "admin.message.varSummary" },
    { key: "thinkDirections", labelKey: "admin.message.varThinkDirections" },
    { key: "gapAreas", labelKey: "admin.message.varGapAreas" },
    { key: "books", labelKey: "admin.message.varBooks" },
  ],
  SMS_LOGIN: [
    { key: "code", labelKey: "admin.message.varCode" },
    { key: "ttlMinutes", labelKey: "admin.message.varTtlMinutes" },
  ],
};

export function varsForScene(scene: MessageSceneCode): MessageTemplateVarDef[] {
  return MESSAGE_SCENE_TEMPLATE_VARS[scene] ?? [];
}

/** 在 input/textarea 光标处插入占位符；无焦点时追加到末尾。 */
/** 与后端 {@code MessageTemplateSupport} 一致，用于管理端预览。 */
/** 与后端 {@link MessageTemplateSupport#applyTemplate} 一致（含 \\n → 换行）。 */
export function applyTemplatePreview(template: string, vars: Record<string, string>): string {
  if (!template) return "";
  let out = template;
  for (const [key, value] of Object.entries(vars)) {
    out = out.split(`{${key}}`).join(normalizeTemplateEscapes(value ?? ""));
  }
  return normalizeTemplateEscapes(out);
}

function normalizeTemplateEscapes(text: string): string {
  return text.replace(/\\r\\n/g, "\n").replace(/\\n/g, "\n").replace(/\\r/g, "\n").replace(/\\t/g, "\t");
}

export const TEMPLATE_PREVIEW_SAMPLE_VARS: Record<string, string> = {
  tenantName: "示例学校",
  code: "123456",
  ttlMinutes: "5",
  userName: "张老师",
  email: "teacher@example.com",
  weekLabel: "2026-05-19 至 2026-05-25",
  summary: "本周聚焦机器学习基础与 RAG 检索增强。",
  thinkDirections: "· 多模态检索\n· 向量索引优化",
  gapAreas: "· 分布式训练",
  books: "· 《深度学习》（示例）",
};

export function insertAtCaret(
  el: HTMLInputElement | HTMLTextAreaElement | null | undefined,
  current: string,
  token: string,
): string {
  if (!el || typeof el.selectionStart !== "number") {
    const sep = current && !current.endsWith(" ") ? " " : "";
    return current + sep + token;
  }
  const start = el.selectionStart ?? current.length;
  const end = el.selectionEnd ?? start;
  return current.slice(0, start) + token + current.slice(end);
}
