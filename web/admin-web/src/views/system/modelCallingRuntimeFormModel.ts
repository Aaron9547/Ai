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
