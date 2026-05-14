/** 管理端 `GET /api/v1/admin/user-profiles/{userId}` 详情 JSON 的归一化形态（对齐后端 UserProfileAdminApplicationService）。 */
export interface AdminUserProfileDetail {
  userId: number;
  loginName: string;
  displayName: string;
  profileTags: { code: string; value: string }[];
  /** 解析后的长期记忆抽象 JSON；可能为 null。 */
  memoryAbstract: unknown;
  /** 当 body_json 无法解析为 JSON 时后端返回的原始字符串。 */
  memoryAbstractRaw?: string;
  memoryChunkTotal: number;
  recentMemoryChunks: AdminRecentMemoryChunk[];
}

export interface AdminRecentMemoryChunk {
  id: number;
  chunkRole: string;
  snippet: string;
  conversationId: number | null;
  createdAt: string | null;
}

/** 画像标签（库表 tag_code）→ 中文标题 */
export function profileTagTitle(code: string): string {
  const c = (code ?? "").trim();
  if (c === "TURN_COUNT") return "累计发言轮次";
  if (c === "LAST_USER_EXCERPT") return "最近用户输入摘要";
  return c || "（未命名标签）";
}

export function profileTagDescription(code: string): string {
  const c = (code ?? "").trim();
  if (c === "TURN_COUNT") {
    return "该用户在当前租户下、跨会话累计的用户侧发言次数（非单条消息条数）。";
  }
  if (c === "LAST_USER_EXCERPT") {
    return "最近一次用户输入的短摘要，用于跨会话上下文。";
  }
  return "系统画像标签，原始编码见「标签编码」列。";
}

/** 长期记忆抽象 JSON 常见顶层字段（与 memory_abstract_v2 提示词 schema 对齐） */
const MEMORY_ABSTRACT_KEY_ORDER = [
  "schema_version",
  "working_summary",
  "episodic_hooks",
  "stable_facts",
  "profile_delta",
  "forget_candidates",
  "merge_notes",
] as const;

export function memoryAbstractKeyLabel(key: string): string {
  const k = (key ?? "").trim();
  const map: Record<string, string> = {
    schema_version: "数据结构版本",
    working_summary: "近期情景摘要",
    episodic_hooks: "短期话题钩子",
    stable_facts: "长期稳定事实",
    profile_delta: "画像结构化增量",
    forget_candidates: "建议遗忘条目",
    merge_notes: "合并说明（给模型/运营）",
  };
  return map[k] ?? k;
}

export function chunkRoleLabel(role: string): string {
  const r = (role ?? "").trim().toUpperCase();
  if (r === "USER") return "用户";
  if (r === "ASSISTANT") return "助手";
  return role || "—";
}

export function normalizeAdminUserProfileDetail(data: unknown): AdminUserProfileDetail {
  const o =
    data !== null && typeof data === "object" && !Array.isArray(data)
      ? (data as Record<string, unknown>)
      : {};

  const rawTags = o.profileTags;
  const profileTags: { code: string; value: string }[] = [];
  if (Array.isArray(rawTags)) {
    for (const t of rawTags) {
      if (t === null || typeof t !== "object") continue;
      const row = t as Record<string, unknown>;
      profileTags.push({
        code: String(row.code ?? ""),
        value: String(row.value ?? ""),
      });
    }
  }

  const rawChunks = o.recentMemoryChunks;
  const recentMemoryChunks: AdminRecentMemoryChunk[] = [];
  if (Array.isArray(rawChunks)) {
    for (const c of rawChunks) {
      if (c === null || typeof c !== "object") continue;
      const row = c as Record<string, unknown>;
      const conv = row.conversationId;
      recentMemoryChunks.push({
        id: Number(row.id),
        chunkRole: String(row.chunkRole ?? "USER"),
        snippet: String(row.snippet ?? ""),
        conversationId: conv === null || conv === undefined ? null : Number(conv),
        createdAt: row.createdAt == null ? null : String(row.createdAt),
      });
    }
  }

  const memoryAbstractRaw =
    typeof o.memoryAbstractRaw === "string" ? o.memoryAbstractRaw : undefined;

  return {
    userId: Number(o.userId ?? 0),
    loginName: String(o.loginName ?? ""),
    displayName: String(o.displayName ?? ""),
    profileTags,
    memoryAbstract: Object.prototype.hasOwnProperty.call(o, "memoryAbstract")
      ? o.memoryAbstract
      : null,
    memoryAbstractRaw,
    memoryChunkTotal: Number(o.memoryChunkTotal ?? 0),
    recentMemoryChunks,
  };
}

export function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === "object" && !Array.isArray(v);
}

export type MemoryAbstractRow = { key: string; label: string; value: unknown };

/** 按产品语义顺序输出抽象层字段，其余字段字母序排在后面。 */
export function memoryAbstractRows(obj: Record<string, unknown>): MemoryAbstractRow[] {
  const seen = new Set<string>();
  const out: MemoryAbstractRow[] = [];
  for (const k of MEMORY_ABSTRACT_KEY_ORDER) {
    if (Object.prototype.hasOwnProperty.call(obj, k)) {
      seen.add(k);
      out.push({ key: k, label: memoryAbstractKeyLabel(k), value: obj[k] });
    }
  }
  const rest = Object.keys(obj)
    .filter((k) => !seen.has(k))
    .sort();
  for (const k of rest) {
    out.push({ key: k, label: memoryAbstractKeyLabel(k), value: obj[k] });
  }
  return out;
}

export function isArrayOfPrimitives(arr: unknown[]): boolean {
  return arr.every((x) => {
    const t = typeof x;
    return t === "string" || t === "number" || t === "boolean" || x === null;
  });
}

export function formatUnknownJson(value: unknown, indent = 2): string {
  try {
    return JSON.stringify(value, null, indent);
  } catch {
    return String(value);
  }
}

export function sortedObjectEntries(obj: Record<string, unknown>): [string, unknown][] {
  return Object.keys(obj)
    .sort()
    .map((k) => [k, obj[k] as unknown]);
}

/** 对象嵌套值在表格/描述列表中的展示：基本类型直接转字符串，对象/数组转 JSON。 */
export function formatLeafForAdmin(v: unknown): string {
  if (v === null || v === undefined) return "—";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
