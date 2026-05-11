/** 兼容 API 可能返回的 camelCase / snake_case 字符串字段。 */
function firstNonBlankStr(...candidates: unknown[]): string | undefined {
  for (const c of candidates) {
    if (typeof c === "string") {
      const t = c.trim();
      if (t) return t;
    }
  }
  return undefined;
}

/** 管理端列表：租户名称 + 编码，提升可读性（缺省回退为「—」）。 */
export function formatTenantNameCode(row: {
  tenantName?: string | null;
  tenantCode?: string | null;
  tenant_name?: string | null;
  tenant_code?: string | null;
}): string {
  const name = firstNonBlankStr(row.tenantName, row.tenant_name);
  const code = firstNonBlankStr(row.tenantCode, row.tenant_code);
  if (name && code) {
    return `${name}（${code}）`;
  }
  if (name) {
    return name;
  }
  if (code) {
    return code;
  }
  return "—";
}

/** 管理端列表：用户展示名（昵称优先）；无展示名时不回显数字 userId（见 .cursorrules §7.1）。 */
export function formatUserDisplayName(row: { userDisplayName?: string | null; user_display_name?: string | null }): string {
  const d = firstNonBlankStr(row.userDisplayName, row.user_display_name);
  if (d) {
    return d;
  }
  return "—";
}

/** 计量列表「用户」列：有用户展示名优先；否则用设备码（访客/匿名计量）。 */
export function formatMeteringUserOrDevice(row: {
  userDisplayName?: string | null;
  user_display_name?: string | null;
  deviceId?: string | null;
  device_id?: string | null;
}): string {
  const u = firstNonBlankStr(row.userDisplayName, row.user_display_name);
  if (u) return u;
  const d = firstNonBlankStr(row.deviceId, row.device_id);
  if (d) return `设备 · ${d}`;
  return "—";
}

/** 访问日志「用户」列：已登录展示名；否则带设备码的访客标签。 */
export function formatAccessLogUser(row: {
  userDisplayName?: string | null;
  user_display_name?: string | null;
  deviceId?: string | null;
  device_id?: string | null;
}): string {
  const u = firstNonBlankStr(row.userDisplayName, row.user_display_name);
  if (u) return u;
  const d = firstNonBlankStr(row.deviceId, row.device_id);
  if (d) return `访客 · ${d}`;
  return "未登录";
}

/** 租户下拉/筛选用：名称 + `sys_tenant.code`，禁止在选项文案中拼接裸数字 `id`。 */
export function formatTenantRowOptionLabel(t: { name: string; code: string }): string {
  const name = (t.name ?? "").trim() || "—";
  const code = (t.code ?? "").trim() || "—";
  return `${name}（${code}）`;
}

/** 审计：已解析到用户展示名则用之；USER 且仅数字 actorId 时不回显裸主键（见 .cursorrules §7.1）。 */
export function formatAuditActor(row: {
  actorDisplayName?: string | null;
  actor_display_name?: string | null;
  actorId?: string | null;
  actor_id?: string | null;
  actorType?: string | null;
  actor_type?: string | null;
}): string {
  const d = firstNonBlankStr(row.actorDisplayName, row.actor_display_name);
  if (d) {
    return d;
  }
  const at = firstNonBlankStr(row.actorType, row.actor_type);
  const id = firstNonBlankStr(row.actorId, row.actor_id);
  if (id) {
    const userLike = at == null || at.toUpperCase() === "USER";
    if (userLike && /^\d+$/.test(id)) {
      return "用户（展示名暂缺）";
    }
    return id;
  }
  return "—";
}

/** 审计列表「关联对象」：后端可读摘要优先；避免主列仅展示行主键。 */
export function formatAuditResource(row: {
  resourceDisplaySummary?: string | null;
  resource_display_summary?: string | null;
}): string {
  return firstNonBlankStr(row.resourceDisplaySummary, row.resource_display_summary) ?? "—";
}

/** 对话日志：会话总 Token 近似值；含 0（与「无数据」区分）。 */
export function formatConversationTokensApprox(v: number | null | undefined): string {
  if (v == null) return "—";
  return String(v);
}

/**
 * 对话日志「用户」列：有 `userId` 时仅用后端回填的展示名（与 `chat_conversation.tenant_id` 下成员关系一致）；无 `userId` 的访客会话才用设备码。
 */
export function formatChatConversationUser(row: {
  userId?: number | null;
  user_id?: number | null;
  userDisplayName?: string | null;
  user_display_name?: string | null;
  deviceId?: string | null;
  device_id?: string | null;
}): string {
  const rawUid = row.userId ?? row.user_id;
  const hasUser = rawUid != null && Number.isFinite(Number(rawUid));
  if (hasUser) {
    const name = firstNonBlankStr(row.userDisplayName, row.user_display_name);
    if (name) return name;
    return "用户（展示名暂缺）";
  }
  const d = firstNonBlankStr(row.deviceId, row.device_id);
  if (d) return `访客 · ${d}`;
  return "—";
}
