/**
 * 与后端 {@code AdminMenuCode} 枚举名一致；库表 {@code lnk_*_admin_menu.menu_code} 存同名英文值。
 * 界面展示用 {@link adminMenuLabelZh} / {@link ADMIN_MENU_LABELS}。
 */
export const ALL_ADMIN_MENU_CODES = [
  "DASHBOARD",
  "USERS",
  "USER_PROFILES",
  "TENANTS",
  "ACCESS_LOGS",
  "AUDIT_EVENTS",
  "OBSERVABILITY",
  "METERING",
  "LLM_MODELS",
  "PROMPT_TEMPLATES",
  "MCP_SERVERS",
  "RAG_KBS",
  "FILE_OBJECTS",
  "NOTIFICATIONS",
  "EVAL_RUNS",
  "CHAT",
  "CHAT_INTENTS",
  "SYSTEM_SETTINGS",
  "SCHEDULED_TASKS",
  "MESSAGE_CENTER",
  "MENU_CATALOG",
  "GATEWAY_API",
] as const;

export type AdminMenuCode = (typeof ALL_ADMIN_MENU_CODES)[number];

/** 租户「后台开放菜单」勾选区与侧栏一致的分组；`titleI18nKey` 为 vue-i18n 全键（如 views.tenants.menuGroups.xxx）。 */
export type AdminMenuTenantGroupSpec = {
  titleI18nKey: string;
  codes: readonly AdminMenuCode[];
};

export const ADMIN_MENU_TENANT_GROUPS: readonly AdminMenuTenantGroupSpec[] = [
  { titleI18nKey: "views.tenants.menuGroups.overview", codes: ["DASHBOARD"] },
  {
    titleI18nKey: "views.tenants.menuGroups.modelKnowledge",
    codes: ["LLM_MODELS", "PROMPT_TEMPLATES", "MCP_SERVERS", "RAG_KBS"],
  },
  { titleI18nKey: "views.tenants.menuGroups.members", codes: ["USERS", "USER_PROFILES"] },
  { titleI18nKey: "views.tenants.menuGroups.chat", codes: ["CHAT", "CHAT_INTENTS"] },
  { titleI18nKey: "views.tenants.menuGroups.gateway", codes: ["GATEWAY_API", "ACCESS_LOGS"] },
  { titleI18nKey: "views.tenants.menuGroups.auditMetering", codes: ["AUDIT_EVENTS", "OBSERVABILITY", "METERING"] },
  {
    titleI18nKey: "views.tenants.menuGroups.tenantCapabilities",
    codes: ["SYSTEM_SETTINGS", "SCHEDULED_TASKS", "MESSAGE_CENTER", "FILE_OBJECTS", "NOTIFICATIONS", "EVAL_RUNS"],
  },
  { titleI18nKey: "views.tenants.menuGroups.platform", codes: ["TENANTS", "MENU_CATALOG"] },
] as const;

export const ADMIN_MENU_LABELS: Record<string, string> = {
  DASHBOARD: "数据概览",
  USERS: "成员与账号",
  USER_PROFILES: "画像与记忆",
  TENANTS: "租户列表",
  ACCESS_LOGS: "访问日志",
  AUDIT_EVENTS: "操作审计",
  OBSERVABILITY: "链路可观测",
  METERING: "用量明细",
  LLM_MODELS: "大模型配置",
  PROMPT_TEMPLATES: "提示词工程",
  MCP_SERVERS: "MCP 服务",
  RAG_KBS: "知识库",
  FILE_OBJECTS: "文件对象",
  NOTIFICATIONS: "通知订阅",
  EVAL_RUNS: "评测运行",
  CHAT: "会话记录",
  CHAT_INTENTS: "对话意图",
  SYSTEM_SETTINGS: "运行时参数",
  SCHEDULED_TASKS: "定时任务",
  MESSAGE_CENTER: "消息发送",
  MENU_CATALOG: "后台菜单",
  GATEWAY_API: "API 限流",
};

/** 枚举名 → 中文展示名（表格、勾选列表等）；未知项回退为简短提示。 */
export function adminMenuLabelZh(code: string | null | undefined): string {
  if (code == null || !String(code).trim()) {
    return "—";
  }
  const c = String(code).trim();
  return ADMIN_MENU_LABELS[c] ?? `未同步菜单项（码：${c}）`;
}
