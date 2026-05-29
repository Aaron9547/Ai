import {
  getOrCreateDeviceId,
  getUserAccessToken,
  http,
  resolveApiBaseForBrowser,
} from "../plugins/http";
import { buildOutboundTenantHeaders } from "../utils/outboundTenant";

function sseOutboundTenantHeaders(): Record<string, string> {
  const out: Record<string, string> = {};
  const t = buildOutboundTenantHeaders();
  if (t["X-Tenant-Id"]) out["X-Tenant-Id"] = t["X-Tenant-Id"];
  if (t["X-Tenant-Code"]) out["X-Tenant-Code"] = t["X-Tenant-Code"];
  return out;
}

export async function createConversation(title?: string) {
  const { data } = await http.post("/open/v1/chat/conversations", { title });
  return data as { id: string };
}

export type ConversationListItem = {
  id: string;
  title: string;
  updatedAt?: string | null;
};

export async function listConversations() {
  const { data } = await http.get("/open/v1/chat/conversations");
  return data as ConversationListItem[];
}

export async function renameConversation(id: string, title: string) {
  const { data } = await http.patch<ConversationListItem>(`/open/v1/chat/conversations/${encodeURIComponent(id)}`, {
    title,
  });
  return data;
}

export async function archiveConversation(id: string) {
  await http.delete(`/open/v1/chat/conversations/${encodeURIComponent(id)}`);
}

/** 与后端 {@code priorVersions} 数组项一致，重新生成前的助手快照 */
export interface PriorAssistantVersion {
  content: string;
  reasoning?: string | null;
  modelAlias?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
}

/** 与助手消息 meta {@code ragCitations} 项一致（RAG 意图命中知识分片） */
export interface RagCitationItem {
  kbId: number;
  documentId: number;
  documentTitle: string;
  chunkId: number;
  chunkSeq: number;
  contentPreview: string;
}

/** 与助手消息 meta {@code webSearchReferences} 及 SSE {@code webSearchRefs} 内 {@code references[]} 项一致 */
export interface WebSearchRefItem {
  title: string;
  url: string;
  summary: string;
  siteName?: string | null;
  logoUrl?: string | null;
  publishTime?: string | null;
  extraJson?: string | null;
}

/** 与 SSE / meta {@code workflowSegments} 单项一致（意图工作流阶段）。 */
export interface WorkflowStagePayload {
  segmentId: string;
  title?: string | null;
  mode: string;
  status: string;
  text: string;
  /** 仅前端：步骤条是否折叠正文（落库 meta 不含此字段）。 */
  wfCollapsed?: boolean;
}

/** 与后端 {@code ChatIntentTurnHitView} 一致（用户/助手消息 meta 解析） */
export interface ChatIntentTurnHit {
  intentId: number;
  intentCode: string;
  keywordId: number | null;
  keywordPhrase: string;
  keywordKind: string | null;
  matchSource: string;
  intentFlowTicket?: string | null;
  intentFlowEpisodeId?: string | null;
  intentFlowRound?: string | null;
  intentFlowRoundSeq?: number | null;
}

/** 用户消息随附的上传文件摘要（与开放接口 {@code ChatMessageView.attachments} 项一致） */
export interface ChatAttachmentMessage {
  id: number;
  fileName: string;
  charLength: number | null;
}

export interface ConversationTokenSceneRow {
  scene: string;
  label: string;
  totalTokens: number;
  promptTokens: number;
  completionTokens: number;
}

export interface ConversationTokenSummary {
  totalTokens: number;
  promptTokens: number;
  completionTokens: number;
  byScene: ConversationTokenSceneRow[];
}

export interface ChatHistoryMessage {
  id: number;
  role: "user" | "assistant" | "system";
  content: string;
  reasoning: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  /** 本回合全部模型调用 token（联网、猜你想问等） */
  turnTotalTokens?: number | null;
  createdAt: string;
  modelAlias?: string | null;
  /** 助手消息点踩，后端存 DISLIKE */
  userFeedback?: string | null;
  /** 重新生成链上保留的旧版（不含当前正文行） */
  priorVersions?: PriorAssistantVersion[] | null;
  ragCitations?: RagCitationItem[] | null;
  /** 联网检索引用（助手 meta {@code webSearchReferences}） */
  webSearchReferences?: WebSearchRefItem[] | null;
  /** 联网知识库本地命中引用（助手 meta {@code knowledgeBaseReferences}） */
  knowledgeBaseReferences?: WebSearchRefItem[] | null;
  /** 助手回复摘要（meta contentSummary），异步生成 */
  contentSummary?: string | null;
  /** 意图工作流阶段快照（助手 meta） */
  workflowSegments?: WorkflowStagePayload[] | null;
  /** 本回合意图命中摘要（用户/助手 meta） */
  intentTurnHit?: ChatIntentTurnHit | null;
  /** 用户消息关联的上传附件 */
  attachments?: ChatAttachmentMessage[] | null;
}

export async function listConversationMessages(conversationId: string): Promise<ChatHistoryMessage[]> {
  const { data } = await http.get<ChatHistoryMessage[]>(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages`,
  );
  return data;
}

export async function fetchConversationTokenSummary(conversationId: string): Promise<ConversationTokenSummary> {
  const { data } = await http.get<ConversationTokenSummary>(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/token-total`,
  );
  const totalTokens = typeof data.totalTokens === "number" && data.totalTokens > 0 ? data.totalTokens : 0;
  const promptTokens = typeof data.promptTokens === "number" ? data.promptTokens : 0;
  const completionTokens = typeof data.completionTokens === "number" ? data.completionTokens : 0;
  const byScene = Array.isArray(data.byScene)
    ? data.byScene
        .filter((row) => row && typeof row.totalTokens === "number" && row.totalTokens > 0)
        .map((row) => ({
          scene: String(row.scene ?? ""),
          label: String(row.label ?? row.scene ?? ""),
          totalTokens: row.totalTokens,
          promptTokens: typeof row.promptTokens === "number" ? row.promptTokens : 0,
          completionTokens: typeof row.completionTokens === "number" ? row.completionTokens : 0,
        }))
    : [];
  return { totalTokens, promptTokens, completionTokens, byScene };
}

/** 使用 {@link fetchConversationTokenSummary} 的合计字段。 */
export async function fetchConversationTokenTotal(conversationId: string): Promise<number> {
  const summary = await fetchConversationTokenSummary(conversationId);
  return summary.totalTokens;
}

export interface ChatShareCreateResult {
  shareCode: string;
  sharePath: string;
  expiresAt: string | null;
}

export async function createConversationShare(
  conversationId: string,
  body: { messageIds: number[] },
): Promise<ChatShareCreateResult> {
  const { data } = await http.post<ChatShareCreateResult>(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/shares`,
    body,
  );
  return data;
}

export async function getPublicShare(shareCode: string): Promise<{
  title: string;
  messages: ChatHistoryMessage[];
  sharedAt: string;
}> {
  const { data } = await http.get(`/open/v1/chat/shares/${encodeURIComponent(shareCode)}`);
  return data as { title: string; messages: ChatHistoryMessage[]; sharedAt: string };
}

export interface LlmModelOption {
  alias: string;
  displayName: string;
  maxAttachments: number;
  supportsThinking: boolean;
  allowAnonymous: boolean;
  /** 共用 token 额度已用尽 */
  quotaExhausted?: boolean;
}

export async function listChatModels(): Promise<LlmModelOption[]> {
  const { data } = await http.get<LlmModelOption[]>("/open/v1/chat/models");
  return data;
}

export async function getWebSearchAvailability(): Promise<{ allowed: boolean }> {
  const { data } = await http.get<{ allowed: boolean }>("/open/v1/chat/web-search-availability");
  return data;
}

export interface UploadAttResponse {
  id: number;
  fileName: string;
  charLength: number;
}

export async function uploadChatAttachments(
  conversationId: string,
  files: File[],
): Promise<UploadAttResponse[]> {
  const fd = new FormData();
  for (const f of files) {
    fd.append("files", f);
  }
  const { data } = await http.post<UploadAttResponse[]>(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/attachments`,
    fd,
  );
  return data;
}

export type ChatResponseLocale = "zh-CN" | "en-US";

export interface ChatSendPayload {
  content: string;
  modelAlias: string;
  thinkingEnabled: boolean;
  /** 客户端幂等键，避免连点/重试重复落库 */
  clientSendKey?: string;
  /** 主模型前是否执行联网检索（须租户已配置联网搜索模型） */
  webSearchEnabled?: boolean;
  mcpEnabled?: boolean;
  mcpServerIds?: number[];
  attachmentIds: number[];
  /** 多轮意图流票据（来自上一条助手消息 meta） */
  intentFlowTicket?: string | null;
  /** 与 UI 语言一致，约束助手回复语种 */
  responseLocale?: ChatResponseLocale;
}

export type TokenUsageChunk = {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
};

export type WebSearchStatusPhase = "searching" | "done";

export type StreamPart =
  | { type: "content"; v?: string }
  | { type: "reasoning"; v?: string }
  | { type: "ragDoc"; documentId?: number; title?: string }
  | { type: "webSearchRefs"; references: WebSearchRefItem[] }
  | { type: "knowledgeRefs"; references: WebSearchRefItem[] }
  | { type: "webSearchStatus"; phase: WebSearchStatusPhase }
  | { type: "workflowStage"; stage: WorkflowStagePayload }
  | { type: "inputBlocked"; reason?: string }
  | {
      type: "error";
      code?: string;
      message?: string;
      httpStatus?: number;
      retryAfterMs?: number;
      kind?: string;
      bodySnippet?: string;
    }
  | { type: "followUpPrompts"; items: StarterPromptItem[] }
  | { type: "end"; usage?: TokenUsageChunk; assistantMessageId?: number; durationMs?: number; conversationTokenTotal?: number; turnTokenTotal?: number };

function normalizeWebSearchRefItems(refs: unknown[]): WebSearchRefItem[] {
  return refs
    .filter((x) => x && typeof x === "object")
    .map((x) => {
      const item = x as WebSearchRefItem;
      return {
        title: typeof item.title === "string" ? item.title : "",
        url: typeof item.url === "string" ? item.url : "",
        summary: typeof item.summary === "string" ? item.summary : "",
        siteName: typeof item.siteName === "string" ? item.siteName : null,
        logoUrl: typeof item.logoUrl === "string" ? item.logoUrl : null,
        publishTime: typeof item.publishTime === "string" ? item.publishTime : null,
        extraJson: typeof item.extraJson === "string" ? item.extraJson : null,
      };
    });
}

function parseReferencesSsePayload(raw: string): WebSearchRefItem[] {
  try {
    const j = JSON.parse(raw) as { references?: WebSearchRefItem[] };
    const refs = Array.isArray(j.references) ? j.references : [];
    return normalizeWebSearchRefItems(refs);
  } catch {
    return [];
  }
}

function parseSsePayload(raw: string): StreamPart | null {
  const t = raw.trim();
  if (!t) return null;
  try {
    const o = JSON.parse(t) as {
      type?: string;
      v?: string;
      usage?: TokenUsageChunk;
      assistantMessageId?: number;
      reason?: string;
      code?: string;
      message?: string;
      httpStatus?: number;
      retryAfterMs?: number;
      kind?: string;
      bodySnippet?: string;
    };
    if (o.type === "content" || o.type === "reasoning") {
      return { type: o.type, v: o.v };
    }
    if (o.type === "error") {
      return {
        type: "error",
        code: typeof o.code === "string" ? o.code : undefined,
        message: typeof o.message === "string" ? o.message : undefined,
        httpStatus: typeof o.httpStatus === "number" ? o.httpStatus : undefined,
        retryAfterMs: typeof o.retryAfterMs === "number" ? o.retryAfterMs : undefined,
        kind: typeof o.kind === "string" ? o.kind : undefined,
        bodySnippet: typeof o.bodySnippet === "string" ? o.bodySnippet : undefined,
      };
    }
    if (o.type === "ragDoc") {
      try {
        const j = JSON.parse(o.v ?? "{}") as { documentId?: number; title?: string };
        return {
          type: "ragDoc",
          documentId: typeof j.documentId === "number" ? j.documentId : undefined,
          title: typeof j.title === "string" ? j.title : undefined,
        };
      } catch {
        return { type: "ragDoc", title: o.v };
      }
    }
    if (o.type === "webSearchRefs" && typeof o.v === "string") {
      const normalized = parseReferencesSsePayload(o.v);
      if (normalized.length) {
        return { type: "webSearchRefs", references: normalized };
      }
    }
    if (o.type === "knowledgeRefs" && typeof o.v === "string") {
      const normalized = parseReferencesSsePayload(o.v);
      if (normalized.length) {
        return { type: "knowledgeRefs", references: normalized };
      }
    }
    if (o.type === "webSearchStatus" && typeof o.v === "string") {
      try {
        const j = JSON.parse(o.v) as { phase?: string };
        const phase = j.phase === "searching" || j.phase === "done" ? j.phase : null;
        if (phase) {
          return { type: "webSearchStatus", phase };
        }
      } catch {
        /* ignore */
      }
    }
    if (o.type === "workflowStage" && typeof o.v === "string") {
      try {
        const j = JSON.parse(o.v) as WorkflowStagePayload;
        if (j && typeof j.segmentId === "string") {
          return {
            type: "workflowStage",
            stage: {
              segmentId: j.segmentId,
              title: j.title ?? null,
              mode: typeof j.mode === "string" ? j.mode : "block",
              status: typeof j.status === "string" ? j.status : "done",
              text: typeof j.text === "string" ? j.text : "",
            },
          };
        }
      } catch {
        /* ignore */
      }
    }
    if (o.type === "inputBlocked") {
      return { type: "inputBlocked", reason: typeof o.reason === "string" ? o.reason : undefined };
    }
    if (o.type === "followUpPrompts" && typeof o.v === "string") {
      try {
        const j = JSON.parse(o.v) as { items?: StarterPromptItem[] };
        const items = Array.isArray(j.items) ? j.items.filter((x) => x?.text?.trim()) : [];
        if (items.length) {
          return { type: "followUpPrompts", items };
        }
      } catch {
        /* ignore */
      }
    }
    if (o.type === "end") {
      const raw = o as {
        usage?: TokenUsageChunk;
        assistantMessageId?: number;
        durationMs?: number;
        conversationTokenTotal?: number;
        turnTokenTotal?: number;
      };
      return {
        type: "end",
        usage: raw.usage,
        assistantMessageId:
          typeof raw.assistantMessageId === "number" ? raw.assistantMessageId : undefined,
        durationMs: typeof raw.durationMs === "number" ? raw.durationMs : undefined,
        conversationTokenTotal:
          typeof raw.conversationTokenTotal === "number" ? raw.conversationTokenTotal : undefined,
        turnTokenTotal: typeof raw.turnTokenTotal === "number" ? raw.turnTokenTotal : undefined,
      };
    }
  } catch {
    return { type: "content", v: t };
  }
  return null;
}

export type ChatAssistantFeedbackVote = "LIKE" | "DISLIKE" | "NONE";

export async function submitAssistantFeedback(
  conversationId: string,
  messageId: number,
  vote: ChatAssistantFeedbackVote,
): Promise<void> {
  await http.post(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}/feedback`,
    { vote },
  );
}

/** 重新生成时可选覆盖（与后端 {@link ChatRegenerateRequest} 对齐） */
export interface ChatRegenerateBody {
  modelAlias?: string;
  thinkingEnabled?: boolean;
  webSearchEnabled?: boolean;
  mcpEnabled?: boolean;
  mcpServerIds?: number[];
  responseLocale?: ChatResponseLocale;
}

export type ChatStreamOptions = {
  signal?: AbortSignal;
};

export class ChatStreamHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ChatStreamHttpError";
    this.status = status;
  }
}

async function readSseStream(
  res: Response,
  onPart: (p: StreamPart) => void,
  signal?: AbortSignal,
): Promise<void> {
  if (!res.ok || !res.body) {
    let detail = "";
    try {
      detail = (await res.text()).trim();
      if (detail) {
        const parsed = JSON.parse(detail) as { message?: string };
        if (typeof parsed?.message === "string" && parsed.message.trim()) {
          throw new ChatStreamHttpError(res.status, parsed.message.trim());
        }
      }
    } catch (e) {
      if (e instanceof ChatStreamHttpError) {
        throw e;
      }
    }
    throw new ChatStreamHttpError(res.status, `stream request failed: ${res.status}${detail ? ` ${detail}` : ""}`);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      if (signal?.aborted) {
        await reader.cancel();
        throw new DOMException("Aborted", "AbortError");
      }
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buffer.indexOf("\n\n")) >= 0) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        for (const line of block.split("\n")) {
          if (line.startsWith("data:")) {
            const raw = line.slice(5).trimStart();
            const part = parseSsePayload(raw);
            if (part) {
              onPart(part);
            }
          }
        }
      }
    }
  } finally {
    try {
      reader.releaseLock();
    } catch {
      /* ignore */
    }
  }
}

function streamFetchHeaders(): Record<string, string> {
  const token = getUserAccessToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
    ...sseOutboundTenantHeaders(),
    "X-Device-Id": getOrCreateDeviceId(),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

/** 删除最后一条助手消息并基于前一条用户消息重新流式生成（SSE 帧与 {@link streamAssistantReply} 相同）。 */
export async function streamRegenerateAssistantReply(
  conversationId: string,
  assistantMessageId: number,
  body: ChatRegenerateBody | undefined,
  onPart: (p: StreamPart) => void,
  options?: ChatStreamOptions,
): Promise<void> {
  const base = resolveApiBaseForBrowser();
  const res = await fetch(
    `${base}/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages/${assistantMessageId}/retry`,
    {
      method: "POST",
      headers: streamFetchHeaders(),
      body: JSON.stringify(body ?? {}),
      signal: options?.signal,
    },
  );
  await readSseStream(res, onPart, options?.signal);
}

export interface StarterPromptItem {
  id: number | null;
  text: string;
  source: string;
}

export interface StarterPromptList {
  items: StarterPromptItem[];
  fallback: boolean;
}

export async function fetchStarterPrompts(params: {
  scene?: "EMPTY" | "FOLLOW_UP";
  limit?: number;
  refresh?: boolean;
  excludeIds?: number[];
  thinkingEnabled?: boolean;
  webSearchEnabled?: boolean;
  mcpEnabled?: boolean;
  mcpServerIds?: number[];
}): Promise<StarterPromptList> {
  const { data } = await http.get("/open/v1/chat/starter-prompts", { params });
  return data as StarterPromptList;
}

export async function recordStarterPromptEvent(body: {
  promptId?: number | null;
  scene: string;
  eventType: "IMPRESSION" | "CLICK" | "SEND";
}): Promise<void> {
  await http.post("/open/v1/chat/starter-prompts/events", body);
}

export async function fetchFollowUpPrompts(
  conversationId: string,
  messageId: number,
  limit = 3,
): Promise<StarterPromptList> {
  const { data } = await http.get(
    `/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}/follow-up-prompts`,
    { params: { limit } },
  );
  return data as StarterPromptList;
}

/** 使用 fetch 读取 SSE（携带 {@code X-Tenant-Id} 或 {@code X-Tenant-Code} 与 {@code X-Device-Id}）。data 行为 JSON 分帧：content / reasoning / ragDoc / webSearchRefs / webSearchStatus / end */
export async function streamAssistantReply(
  conversationId: string,
  payload: ChatSendPayload,
  onPart: (p: StreamPart) => void,
  options?: ChatStreamOptions,
): Promise<void> {
  const base = resolveApiBaseForBrowser();
  const res = await fetch(
    `${base}/open/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages`,
    {
      method: "POST",
      headers: streamFetchHeaders(),
      body: JSON.stringify(payload),
      signal: options?.signal,
    },
  );
  await readSseStream(res, onPart, options?.signal);
}
