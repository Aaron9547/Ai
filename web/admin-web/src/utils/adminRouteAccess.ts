import { getAdminMenuSession } from "./adminMenuSession";

/** 路由所需菜单码；{@code FOUNDER_ONLY} 表示仅创始人（与 router meta.founderOnly 一致）。 */
export type AdminRouteRequirement = string | "FOUNDER_ONLY";

const FALLBACK_PATH_ORDER = [
  "/dashboard",
  "/model/llm-models",
  "/prompt/templates",
  "/mcp/servers",
  "/knowledge-center/knowledge-bases",
  "/users",
  "/users/profiles",
  "/chat/conversations",
  "/chat/intents",
  "/gateway/access-logs",
  "/audit/events",
  "/billing/metering",
  "/system/runtime-settings",
  "/system/tenant-shell-config",
  "/system/scheduled-tasks",
  "/system/message-channels",
  "/gateway/api-rate-limits",
  "/gateway/cors-origins",
  "/tenant/tenants",
  "/system/menu-items",
] as const;

/** 管理端路径 → 所需 {@code AdminMenuCode}（与侧栏 {@code menuAllowed} 对齐）。 */
export function requiredMenuForAdminPath(path: string): AdminRouteRequirement | null {
  const p = path.split("?")[0] ?? path;
  if (p === "/dashboard" || p.startsWith("/dashboard/")) return "DASHBOARD";
  if (p.startsWith("/users/knowledge-planet-weekly-feedback")) return "USER_PROFILES";
  if (p.startsWith("/users/profiles")) return "USER_PROFILES";
  if (p.startsWith("/users")) return "USERS";
  if (p.startsWith("/tenant/tenants")) return "FOUNDER_ONLY";
  if (p.startsWith("/system/menu-items")) return "FOUNDER_ONLY";
  if (p.startsWith("/gateway/api-rate-limits") || p.startsWith("/gateway/cors-origins") || p.startsWith("/gateway/access-parties")) return "GATEWAY_API";
  if (p.startsWith("/gateway/access-logs")) return "ACCESS_LOGS";
  if (p.startsWith("/audit/events")) return "AUDIT_EVENTS";
  if (p.startsWith("/billing/metering")) return "METERING";
  if (p.startsWith("/chat/intents")) return "CHAT_INTENTS";
  if (p.startsWith("/chat/")) return "CHAT";
  if (p.startsWith("/model/llm-models")) return "LLM_MODELS";
  if (p.startsWith("/prompt/templates")) return "PROMPT_TEMPLATES";
  if (p.startsWith("/mcp/servers")) return "MCP_SERVERS";
  if (p.startsWith("/knowledge-center")) return "RAG_KBS";
  if (p.startsWith("/system/scheduled-tasks")) return "SCHEDULED_TASKS";
  if (p.startsWith("/system/message-")) return "MESSAGE_CENTER";
  if (p.startsWith("/system/runtime-settings") || p.startsWith("/system/tenant-shell-config")) {
    return "SYSTEM_SETTINGS";
  }
  return null;
}

export function isAdminPathAllowed(
  path: string,
  allowedMenuCodes: string[] | null,
  isFounder: boolean,
): boolean {
  if (import.meta.env.VITE_ADMIN_AUTH_SKIP === "true") {
    return true;
  }
  const req = requiredMenuForAdminPath(path);
  if (req === null) {
    return true;
  }
  if (req === "FOUNDER_ONLY") {
    return isFounder;
  }
  if (isFounder) {
    return true;
  }
  if (allowedMenuCodes === null) {
    return true;
  }
  return allowedMenuCodes.includes(req);
}

/** 切换工作区后若无权访问当前页，跳转到第一个可访问页（通常数据概览）。 */
export function defaultAccessibleAdminPath(
  allowedMenuCodes: string[] | null,
  isFounder: boolean,
): string {
  if (isFounder) {
    return "/dashboard";
  }
  for (const p of FALLBACK_PATH_ORDER) {
    if (isAdminPathAllowed(p, allowedMenuCodes, isFounder)) {
      return p;
    }
  }
  return "/dashboard";
}

export function resolvePathAfterWorkspaceSwitch(
  currentPath: string,
  allowedMenuCodes: string[] | null,
  isFounder: boolean,
): string {
  if (isAdminPathAllowed(currentPath, allowedMenuCodes, isFounder)) {
    return currentPath;
  }
  return defaultAccessibleAdminPath(allowedMenuCodes, isFounder);
}

export function guardAdminRoutePath(
  path: string,
  isFounder: boolean,
): string | null {
  const codes = getAdminMenuSession();
  if (isAdminPathAllowed(path, codes, isFounder)) {
    return null;
  }
  const fallback = defaultAccessibleAdminPath(codes, isFounder);
  return path === fallback ? null : fallback;
}
