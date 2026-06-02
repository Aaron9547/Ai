/** 管理端 `GET /api/v1/admin/user-profiles/{userId}` 详情 JSON 的归一化形态（对齐后端 UserProfileAdminApplicationService）。 */
import { i18n } from "@/i18n";

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

function pt(key: string, fallback?: string): string {
  const msg = i18n.global.t(key);
  return msg === key && fallback != null ? fallback : String(msg);
}

/** 画像标签（库表 tag_code）→ 展示标题 */
export function profileTagTitle(code: string): string {
  const c = (code ?? "").trim();
  if (c === "TURN_COUNT") return pt("views.profiles.tags.TURN_COUNT.title");
  if (c === "LAST_USER_EXCERPT") return pt("views.profiles.tags.LAST_USER_EXCERPT.title");
  if (c === "INTEREST_NEWS_JSON") return pt("views.profiles.tags.INTEREST_NEWS_JSON.title");
  if (c === "WEEKLY_INSIGHT_FEEDBACK_JSON") {
    return pt("views.profiles.tags.WEEKLY_INSIGHT_FEEDBACK_JSON.title");
  }
  if (c === "LEARNING_GOAL") return pt("views.profiles.tags.LEARNING_GOAL.title");
  return c || pt("views.profiles.tags._unknownTitle");
}

export function profileTagDescription(code: string): string {
  const c = (code ?? "").trim();
  if (c === "TURN_COUNT") return pt("views.profiles.tags.TURN_COUNT.desc");
  if (c === "LAST_USER_EXCERPT") return pt("views.profiles.tags.LAST_USER_EXCERPT.desc");
  if (c === "INTEREST_NEWS_JSON") return pt("views.profiles.tags.INTEREST_NEWS_JSON.desc");
  if (c === "WEEKLY_INSIGHT_FEEDBACK_JSON") {
    return pt("views.profiles.tags.WEEKLY_INSIGHT_FEEDBACK_JSON.desc");
  }
  if (c === "LEARNING_GOAL") return pt("views.profiles.tags.LEARNING_GOAL.desc");
  return pt("views.profiles.tags._genericDesc");
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
  const i18nKey = `views.profiles.abstractKeys.${k}`;
  const translated = i18n.global.t(i18nKey);
  if (translated !== i18nKey) return String(translated);
  return k;
}

export function chunkRoleLabel(role: string): string {
  const r = (role ?? "").trim().toUpperCase();
  if (r === "USER") return pt("views.profiles.chunkRoles.USER");
  if (r === "ASSISTANT") return pt("views.profiles.chunkRoles.ASSISTANT");
  return role || pt("common.dash", "—");
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
  if (v === null || v === undefined) return pt("common.dash", "—");
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}
