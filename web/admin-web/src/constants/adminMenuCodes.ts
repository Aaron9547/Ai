/**
 * 与后端 {@code AdminMenuCode} 枚举名一致；**库表 `lnk_*_admin_menu.menu_code` 存英文码**（稳定、与 JWT/网关一致），
 * **界面展示一律用 {@link adminMenuLabelZh} / {@link ADMIN_MENU_LABELS} 转中文**。
 */
export const ALL_ADMIN_MENU_CODES = [
  "USERS",
  "TENANTS",
  "ACCESS_LOGS",
  "AUDIT_EVENTS",
  "METERING",
  "LLM_MODELS",
  "MCP_SERVERS",
  "RAG_KBS",
  "FILE_OBJECTS",
  "NOTIFICATIONS",
  "EVAL_RUNS",
  "CHAT",
  "SYSTEM_SETTINGS",
  "MENU_CATALOG",
  "GATEWAY_API",
] as const;

export type AdminMenuCode = (typeof ALL_ADMIN_MENU_CODES)[number];

export const ADMIN_MENU_LABELS: Record<string, string> = {
  USERS: "用户管理",
  TENANTS: "租户管理",
  ACCESS_LOGS: "访问日志",
  AUDIT_EVENTS: "审计事件",
  METERING: "计量",
  LLM_MODELS: "模型管理",
  MCP_SERVERS: "MCP 服务",
  RAG_KBS: "知识中心",
  FILE_OBJECTS: "文件对象",
  NOTIFICATIONS: "通知订阅",
  EVAL_RUNS: "评测运行",
  CHAT: "对话日志",
  SYSTEM_SETTINGS: "系统参数",
  MENU_CATALOG: "菜单管理",
  GATEWAY_API: "接口与限流",
};

/** 菜单码 → 中文名（供表格、勾选列表等）；未知码仍给可读中文说明，避免界面出现裸英文码。 */
export function adminMenuLabelZh(code: string | null | undefined): string {
  if (code == null || !String(code).trim()) {
    return "—";
  }
  const c = String(code).trim();
  return ADMIN_MENU_LABELS[c] ?? `未同步菜单项（码：${c}）`;
}
