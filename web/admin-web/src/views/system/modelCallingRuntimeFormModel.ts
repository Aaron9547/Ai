/**
 * 与后端 ChatPromptLimitsRuntime / MemoryPolicyRuntime / ChatInputGuardRuntime 对齐的表单模型。
 */

export type ChatPromptLimitsForm = {
  ragSnippetMaxChars: number;
  ragMaxSnippets: number;
  webSummaryMaxChars: number;
  webMaxReferences: number;
  webReferenceSnippetMaxChars: number;
  webReferenceUrlMaxChars: number;
  webGroundingTotalMaxChars: number;
  historyMaxMessages: number;
  historyMaxCharsPerMessage: number;
  historyTotalMaxChars: number;
};

export const CHAT_PROMPT_DEFAULT: ChatPromptLimitsForm = {
  ragSnippetMaxChars: 900,
  ragMaxSnippets: 3,
  webSummaryMaxChars: 1600,
  webMaxReferences: 6,
  webReferenceSnippetMaxChars: 280,
  webReferenceUrlMaxChars: 200,
  webGroundingTotalMaxChars: 7200,
  historyMaxMessages: 40,
  historyMaxCharsPerMessage: 12_000,
  historyTotalMaxChars: 48_000,
};

export type MemoryPolicyForm = {
  abstractRefreshEnabled: boolean;
  abstractChunkWindow: number;
  abstractSyncFallback: boolean;
  abstractDedupeTtlSeconds: number;
  enqueueAbstractOnConversationCreate: boolean;
  promptAbstractBodyMaxChars: number;
  promptConcreteChunkLimit: number;
  promptConcreteChunkMaxChars: number;
  vectorSearchTopK: number;
};

export const MEMORY_POLICY_DEFAULT: MemoryPolicyForm = {
  abstractRefreshEnabled: true,
  abstractChunkWindow: 24,
  abstractSyncFallback: true,
  abstractDedupeTtlSeconds: 45,
  enqueueAbstractOnConversationCreate: false,
  promptAbstractBodyMaxChars: 2800,
  promptConcreteChunkLimit: 4,
  promptConcreteChunkMaxChars: 420,
  vectorSearchTopK: 8,
};

export type InputGuardForm = {
  enabled: boolean;
  minUserTextChars: number;
  maxUserTextChars: number;
  blockedReplyTemplate: string;
  sensitiveWordsText: string;
  regexPatternsText: string;
};

export const INPUT_GUARD_DEFAULT: InputGuardForm = {
  enabled: true,
  minUserTextChars: 1,
  maxUserTextChars: 8000,
  blockedReplyTemplate: "",
  sensitiveWordsText: "",
  regexPatternsText: "",
};

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === "object" && !Array.isArray(x);
}

function num(v: unknown, d: number): number {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    if (Number.isFinite(n)) return n;
  }
  return d;
}

function bool(v: unknown, d: boolean): boolean {
  if (typeof v === "boolean") return v;
  if (typeof v === "string") return v.trim().toLowerCase() === "true";
  return d;
}

export function parseChatPromptLimitsJson(raw: string): ChatPromptLimitsForm {
  const d = { ...CHAT_PROMPT_DEFAULT };
  try {
    const o = JSON.parse(raw || "{}");
    if (!isRecord(o)) return d;
    return {
      ragSnippetMaxChars: num(o.ragSnippetMaxChars, d.ragSnippetMaxChars),
      ragMaxSnippets: num(o.ragMaxSnippets, d.ragMaxSnippets),
      webSummaryMaxChars: num(o.webSummaryMaxChars, d.webSummaryMaxChars),
      webMaxReferences: num(o.webMaxReferences, d.webMaxReferences),
      webReferenceSnippetMaxChars: num(o.webReferenceSnippetMaxChars, d.webReferenceSnippetMaxChars),
      webReferenceUrlMaxChars: num(o.webReferenceUrlMaxChars, d.webReferenceUrlMaxChars),
      webGroundingTotalMaxChars: num(o.webGroundingTotalMaxChars, d.webGroundingTotalMaxChars),
      historyMaxMessages: num(o.historyMaxMessages, d.historyMaxMessages),
      historyMaxCharsPerMessage: num(o.historyMaxCharsPerMessage, d.historyMaxCharsPerMessage),
      historyTotalMaxChars: num(o.historyTotalMaxChars, d.historyTotalMaxChars),
    };
  } catch {
    return d;
  }
}

export function serializeChatPromptLimitsJson(form: ChatPromptLimitsForm): string {
  return JSON.stringify({ ...form });
}

export function parseMemoryPolicyJson(raw: string): MemoryPolicyForm {
  const d = { ...MEMORY_POLICY_DEFAULT };
  try {
    const o = JSON.parse(raw || "{}");
    if (!isRecord(o)) return d;
    return {
      abstractRefreshEnabled: bool(o.abstractRefreshEnabled, d.abstractRefreshEnabled),
      abstractChunkWindow: num(o.abstractChunkWindow, d.abstractChunkWindow),
      abstractSyncFallback: bool(o.abstractSyncFallback, d.abstractSyncFallback),
      abstractDedupeTtlSeconds: num(o.abstractDedupeTtlSeconds, d.abstractDedupeTtlSeconds),
      enqueueAbstractOnConversationCreate: bool(
        o.enqueueAbstractOnConversationCreate,
        d.enqueueAbstractOnConversationCreate,
      ),
      promptAbstractBodyMaxChars: num(o.promptAbstractBodyMaxChars, d.promptAbstractBodyMaxChars),
      promptConcreteChunkLimit: num(o.promptConcreteChunkLimit, d.promptConcreteChunkLimit),
      promptConcreteChunkMaxChars: num(o.promptConcreteChunkMaxChars, d.promptConcreteChunkMaxChars),
      vectorSearchTopK: num(o.vectorSearchTopK, d.vectorSearchTopK),
    };
  } catch {
    return d;
  }
}

export function serializeMemoryPolicyJson(form: MemoryPolicyForm): string {
  return JSON.stringify({ ...form });
}

function splitLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean);
}

export function parseInputGuardJson(raw: string): InputGuardForm {
  const d = { ...INPUT_GUARD_DEFAULT };
  try {
    const o = JSON.parse(raw || "{}");
    if (!isRecord(o)) return d;
    const sw: string[] = [];
    if (Array.isArray(o.sensitiveWords)) {
      for (const el of o.sensitiveWords) {
        if (el != null && typeof el === "string" && el.trim()) sw.push(el.trim());
      }
    }
    const patterns: string[] = [];
    if (Array.isArray(o.promptInjectionRegexPatterns)) {
      for (const el of o.promptInjectionRegexPatterns) {
        if (el != null && typeof el === "string" && el.trim()) patterns.push(el);
      }
    }
    let tpl = "";
    if (typeof o.blockedReplyTemplate === "string") {
      tpl = o.blockedReplyTemplate;
    }
    return {
      enabled: bool(o.enabled, d.enabled),
      minUserTextChars: num(o.minUserTextChars, d.minUserTextChars),
      maxUserTextChars: num(o.maxUserTextChars, d.maxUserTextChars),
      blockedReplyTemplate: tpl,
      sensitiveWordsText: sw.join("\n"),
      regexPatternsText: patterns.join("\n"),
    };
  } catch {
    return d;
  }
}

export function serializeInputGuardJson(form: InputGuardForm): string {
  const o: Record<string, unknown> = {
    enabled: form.enabled,
    minUserTextChars: form.minUserTextChars,
    maxUserTextChars: form.maxUserTextChars,
  };
  const tpl = form.blockedReplyTemplate.trim();
  if (tpl.length) {
    o.blockedReplyTemplate = tpl;
  }
  const sw = splitLines(form.sensitiveWordsText);
  if (sw.length) {
    o.sensitiveWords = sw;
  }
  const patterns = splitLines(form.regexPatternsText);
  if (patterns.length) {
    o.promptInjectionRegexPatterns = patterns;
  }
  return JSON.stringify(o);
}

export function parseSuffixJsonArray(raw: string): string[] {
  try {
    const v = JSON.parse(raw || "[]");
    if (!Array.isArray(v)) return [];
    return v.map((x) => (x == null ? "" : String(x)));
  } catch {
    return [];
  }
}

export function resizeSuffixSlots(slots: string[], rounds: number): string[] {
  const n = Math.max(1, Math.min(10, Math.floor(rounds)));
  const next = slots.slice(0, n);
  while (next.length < n) next.push("");
  return next;
}

export function serializeSuffixJson(slots: string[], rounds: number): string {
  const n = Math.max(1, Math.min(10, Math.floor(rounds)));
  const trimmed = resizeSuffixSlots(slots, n);
  return JSON.stringify(trimmed);
}

export function validateMemoryEmbeddingId(s: string): boolean {
  const t = s.trim();
  if (!t) return true;
  return /^[0-9]{1,19}$/.test(t);
}

/** 将运行时中的嵌入模型主键解析为下拉框用的 number；非法或空为 undefined。 */
export function parseMemoryEmbeddingModelId(raw: string | undefined | null): number | undefined {
  return parseBoundLlmModelId(raw);
}

/** 与 {@code WEB_SEARCH_GROUNDING_MODEL_ID} 一致，校验规则同记忆嵌入。 */
export function parseWebSearchGroundingModelId(raw: string | undefined | null): number | undefined {
  return parseBoundLlmModelId(raw);
}

export const WEB_SEARCH_FIXED_SOURCE_CODES = [
  "DUCKDUCKGO_HTML",
  "WIKIPEDIA_REST",
  "GOOGLE_NEWS_RSS",
  "BAIDU_NEWS_HTML",
] as const;

export type WebSearchFixedSourceCode = (typeof WEB_SEARCH_FIXED_SOURCE_CODES)[number];

export const WEB_SEARCH_PROVIDER_MODEL_PREFIX = "model:";
export const WEB_SEARCH_PROVIDER_FIXED_PREFIX = "fixed:";

export function webSearchProviderKeysFromRuntime(
  arkModelIdRaw: string | undefined | null,
  fixedSourcesJson: string | undefined | null,
): string[] {
  const keys: string[] = [];
  const arkId = parseWebSearchGroundingModelId(arkModelIdRaw);
  if (arkId != null) keys.push(`${WEB_SEARCH_PROVIDER_MODEL_PREFIX}${arkId}`);
  for (const code of parseWebSearchFixedSourcesJson(fixedSourcesJson)) {
    keys.push(`${WEB_SEARCH_PROVIDER_FIXED_PREFIX}${code}`);
  }
  return keys;
}

export function normalizeWebSearchProviderKeys(keys: string[]): string[] {
  const fixed = keys.filter((k) => k.startsWith(WEB_SEARCH_PROVIDER_FIXED_PREFIX));
  const models = keys.filter((k) => k.startsWith(WEB_SEARCH_PROVIDER_MODEL_PREFIX));
  const model = models.length > 0 ? [models[models.length - 1]!] : [];
  return [...model, ...fixed];
}

export function webSearchRuntimeFromProviderKeys(keys: string[]): {
  webSearchGroundingModelId: string;
  webSearchGroundingFixedSourcesJson: string;
} {
  let modelId = "";
  const fixed: WebSearchFixedSourceCode[] = [];
  for (const k of keys) {
    if (k.startsWith(WEB_SEARCH_PROVIDER_MODEL_PREFIX)) {
      modelId = k.slice(WEB_SEARCH_PROVIDER_MODEL_PREFIX.length);
    } else if (k.startsWith(WEB_SEARCH_PROVIDER_FIXED_PREFIX)) {
      const code = k.slice(WEB_SEARCH_PROVIDER_FIXED_PREFIX.length);
      if (
        (WEB_SEARCH_FIXED_SOURCE_CODES as readonly string[]).includes(code)
        && !fixed.includes(code as WebSearchFixedSourceCode)
      ) {
        fixed.push(code as WebSearchFixedSourceCode);
      }
    }
  }
  return {
    webSearchGroundingModelId: modelId,
    webSearchGroundingFixedSourcesJson: JSON.stringify(fixed),
  };
}

/** 解析 {@code WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON}。 */
export function parseWebSearchFixedSourcesJson(raw: string | undefined | null): WebSearchFixedSourceCode[] {
  const allowed = new Set<string>(WEB_SEARCH_FIXED_SOURCE_CODES);
  const rawStr = String(raw ?? "").trim();
  if (!rawStr || rawStr === "[]") {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(rawStr);
    if (!Array.isArray(parsed)) {
      return [];
    }
    const out: WebSearchFixedSourceCode[] = [];
    for (const item of parsed) {
      const code = String(item ?? "").trim();
      if (allowed.has(code) && !out.includes(code as WebSearchFixedSourceCode)) {
        out.push(code as WebSearchFixedSourceCode);
      }
    }
    return out;
  } catch {
    return [];
  }
}

function parseBoundLlmModelId(raw: string | undefined | null): number | undefined {
  const s = String(raw ?? "").trim();
  if (!s) return undefined;
  if (!validateMemoryEmbeddingId(s)) return undefined;
  const n = Number(s);
  if (!Number.isSafeInteger(n) || n <= 0) return undefined;
  return n;
}

export function validateInputGuard(form: InputGuardForm): boolean {
  return form.minUserTextChars >= 1 && form.maxUserTextChars >= form.minUserTextChars;
}

/** 与 {@code WEB_SEARCH_GROUNDING_CACHE_JSON} / WebSearchGroundingCachePolicy 对齐 */
export type WebSearchCacheForm = {
  enabled: boolean;
  freshHours: number;
  warmHours: number;
  staleHours: number;
  semanticEnabled: boolean;
  similarityThreshold: number;
  indexMaxEntries: number;
  conversationReuseHours: number;
};

/** 与 {@code WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON} 对齐 */
export type WebSearchFixedOutboundForm = {
  enabled: boolean;
  host: string;
  port: number;
  type: "HTTP" | "SOCKS";
};

export const WEB_SEARCH_FIXED_OUTBOUND_DEFAULT: WebSearchFixedOutboundForm = {
  enabled: false,
  host: "127.0.0.1",
  port: 7890,
  type: "HTTP",
};

function clampProxyPort(n: number, fallback: number): number {
  if (!Number.isFinite(n)) return fallback;
  return Math.max(1, Math.min(65535, Math.floor(n)));
}

export function parseWebSearchFixedOutboundJson(
  raw: string | undefined | null,
): WebSearchFixedOutboundForm {
  const d = { ...WEB_SEARCH_FIXED_OUTBOUND_DEFAULT };
  const t = String(raw ?? "").trim();
  if (!t || t === "{}") return d;
  try {
    const o = JSON.parse(t) as Record<string, unknown>;
    if (typeof o.enabled === "boolean") d.enabled = o.enabled;
    if (typeof o.host === "string") d.host = o.host.trim();
    d.port = clampProxyPort(Number(o.port), d.port);
    const ty = String(o.type ?? "HTTP").trim().toUpperCase();
    d.type = ty === "SOCKS" ? "SOCKS" : "HTTP";
    return d;
  } catch {
    return d;
  }
}

export function serializeWebSearchFixedOutboundJson(form: WebSearchFixedOutboundForm): string {
  return JSON.stringify({
    enabled: form.enabled,
    host: form.host.trim(),
    port: clampProxyPort(form.port, 7890),
    type: form.type === "SOCKS" ? "SOCKS" : "HTTP",
  });
}

export function validateWebSearchFixedOutbound(form: WebSearchFixedOutboundForm): boolean {
  if (!form.enabled) return true;
  return form.host.trim().length > 0 && form.port >= 1 && form.port <= 65535;
}

export const WEB_SEARCH_CACHE_DEFAULT: WebSearchCacheForm = {
  enabled: true,
  freshHours: 6,
  warmHours: 24,
  staleHours: 48,
  semanticEnabled: true,
  similarityThreshold: 0.88,
  indexMaxEntries: 300,
  conversationReuseHours: 6,
};

function clampHours(n: number, fallback: number): number {
  if (!Number.isFinite(n)) return fallback;
  return Math.max(0, Math.min(24 * 14, Math.floor(n)));
}

export function parseWebSearchCacheJson(raw: string | undefined | null): WebSearchCacheForm {
  const d = { ...WEB_SEARCH_CACHE_DEFAULT };
  const t = String(raw ?? "").trim();
  if (!t || t === "{}") return d;
  try {
    const o = JSON.parse(t) as Record<string, unknown>;
    if (typeof o.enabled === "boolean") d.enabled = o.enabled;
    d.freshHours = clampHours(Number(o.freshHours), d.freshHours);
    d.warmHours = clampHours(Number(o.warmHours), d.warmHours);
    d.staleHours = clampHours(Number(o.staleHours), d.staleHours);
    if (d.warmHours < d.freshHours) d.warmHours = d.freshHours;
    if (d.staleHours < d.warmHours) d.staleHours = d.warmHours;
    if (typeof o.semanticEnabled === "boolean") d.semanticEnabled = o.semanticEnabled;
    let sim = Number(o.similarityThreshold);
    if (Number.isFinite(sim) && sim >= 0.5 && sim <= 0.999) d.similarityThreshold = sim;
    let idx = Number(o.indexMaxEntries);
    if (Number.isFinite(idx)) d.indexMaxEntries = Math.max(10, Math.min(500, Math.floor(idx)));
    d.conversationReuseHours = clampHours(Number(o.conversationReuseHours), d.conversationReuseHours);
    return d;
  } catch {
    return d;
  }
}

export type SiteCrawlPresetValue = "CONSERVATIVE" | "BALANCED" | "AGGRESSIVE" | "CUSTOM";

export function normalizeSiteCrawlPreset(raw: string | undefined | null): SiteCrawlPresetValue {
  const t = String(raw ?? "BALANCED").trim().toUpperCase();
  if (t === "CONSERVATIVE" || t === "BALANCED" || t === "AGGRESSIVE" || t === "CUSTOM") {
    return t;
  }
  return "BALANCED";
}

export function parseSiteCrawlRuntimeJson(raw: string | undefined | null): string {
  const t = String(raw ?? "").trim();
  if (!t || t === "{}") return "{}";
  try {
    JSON.parse(t);
    return t;
  } catch {
    return "{}";
  }
}

export function serializeWebSearchCacheJson(form: WebSearchCacheForm): string {
  const fresh = clampHours(form.freshHours, WEB_SEARCH_CACHE_DEFAULT.freshHours);
  let warm = clampHours(form.warmHours, WEB_SEARCH_CACHE_DEFAULT.warmHours);
  let stale = clampHours(form.staleHours, WEB_SEARCH_CACHE_DEFAULT.staleHours);
  if (warm < fresh) warm = fresh;
  if (stale < warm) stale = warm;
  let sim = form.similarityThreshold;
  if (!Number.isFinite(sim) || sim < 0.5 || sim > 0.999) {
    sim = WEB_SEARCH_CACHE_DEFAULT.similarityThreshold;
  }
  let idx = Math.floor(form.indexMaxEntries);
  if (!Number.isFinite(idx)) idx = WEB_SEARCH_CACHE_DEFAULT.indexMaxEntries;
  idx = Math.max(10, Math.min(500, idx));
  return JSON.stringify({
    enabled: !!form.enabled,
    freshHours: fresh,
    warmHours: warm,
    staleHours: stale,
    semanticEnabled: !!form.semanticEnabled,
    similarityThreshold: sim,
    indexMaxEntries: idx,
    conversationReuseHours: clampHours(
      form.conversationReuseHours,
      WEB_SEARCH_CACHE_DEFAULT.conversationReuseHours,
    ),
  });
}
