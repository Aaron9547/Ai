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
  return data as { id: number };
}

export async function listConversations() {
  const { data } = await http.get("/open/v1/chat/conversations");
  return data as { id: number; title: string }[];
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

export interface ChatHistoryMessage {
  id: number;
  role: "user" | "assistant" | "system";
  content: string;
  reasoning: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  createdAt: string;
  modelAlias?: string | null;
  /** 助手消息点踩，后端存 DISLIKE */
  userFeedback?: string | null;
  /** 重新生成链上保留的旧版（不含当前正文行） */
  priorVersions?: PriorAssistantVersion[] | null;
  ragCitations?: RagCitationItem[] | null;
  /** 联网检索引用（助手 meta {@code webSearchReferences}） */
  webSearchReferences?: WebSearchRefItem[] | null;
  /** 助手回复摘要（meta contentSummary），异步生成 */
  contentSummary?: string | null;
  /** 意图工作流阶段快照（助手 meta） */
  workflowSegments?: WorkflowStagePayload[] | null;
  /** 本回合意图命中摘要（用户/助手 meta） */
  intentTurnHit?: ChatIntentTurnHit | null;
  /** 用户消息关联的上传附件 */
  attachments?: ChatAttachmentMessage[] | null;
}

export async function listConversationMessages(conversationId: number): Promise<ChatHistoryMessage[]> {
  const { data } = await http.get<ChatHistoryMessage[]>(
    `/open/v1/chat/conversations/${conversationId}/messages`,
  );
  return data;
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
  conversationId: number,
  files: File[],
): Promise<UploadAttResponse[]> {
  const fd = new FormData();
  for (const f of files) {
    fd.append("files", f);
  }
  const { data } = await http.post<UploadAttResponse[]>(
    `/open/v1/chat/conversations/${conversationId}/attachments`,
    fd,
  );
  return data;
}

export interface ChatSendPayload {
  content: string;
  modelAlias: string;
  thinkingEnabled: boolean;
  /** 主模型前是否执行联网检索（须租户已配置联网搜索模型） */
  webSearchEnabled?: boolean;
  attachmentIds: number[];
  /** 多轮意图流票据（来自上一条助手消息 meta） */
  intentFlowTicket?: string | null;
}

export type TokenUsageChunk = {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
};

export type StreamPart =
  | { type: "content"; v?: string }
  | { type: "reasoning"; v?: string }
  | { type: "ragDoc"; documentId?: number; title?: string }
  | { type: "webSearchRefs"; references: WebSearchRefItem[] }
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
  | { type: "end"; usage?: TokenUsageChunk };

function parseSsePayload(raw: string): StreamPart | null {
  const t = raw.trim();
  if (!t) return null;
  try {
    const o = JSON.parse(t) as {
      type?: string;
      v?: string;
      usage?: TokenUsageChunk;
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
      try {
        const j = JSON.parse(o.v) as { references?: WebSearchRefItem[] };
        const refs = Array.isArray(j.references) ? j.references : [];
        const normalized: WebSearchRefItem[] = refs
          .filter((x) => x && typeof x === "object")
          .map((x) => ({
            title: typeof x.title === "string" ? x.title : "",
            url: typeof x.url === "string" ? x.url : "",
            summary: typeof x.summary === "string" ? x.summary : "",
            siteName: typeof x.siteName === "string" ? x.siteName : null,
            logoUrl: typeof x.logoUrl === "string" ? x.logoUrl : null,
            publishTime: typeof x.publishTime === "string" ? x.publishTime : null,
            extraJson: typeof x.extraJson === "string" ? x.extraJson : null,
          }));
        if (normalized.length) {
          return { type: "webSearchRefs", references: normalized };
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
    if (o.type === "end") {
      return { type: "end", usage: o.usage };
    }
  } catch {
    return { type: "content", v: t };
  }
  return null;
}

export type ChatAssistantFeedbackVote = "LIKE" | "DISLIKE" | "NONE";

export async function submitAssistantFeedback(
  conversationId: number,
  messageId: number,
  vote: ChatAssistantFeedbackVote,
): Promise<void> {
  await http.post(`/open/v1/chat/conversations/${conversationId}/messages/${messageId}/feedback`, { vote });
}

/** 重新生成时可选覆盖（与后端 {@link ChatRegenerateRequest} 对齐） */
export interface ChatRegenerateBody {
  modelAlias?: string;
  thinkingEnabled?: boolean;
  webSearchEnabled?: boolean;
}

/** 删除最后一条助手消息并基于前一条用户消息重新流式生成（SSE 帧与 {@link streamAssistantReply} 相同）。 */
export async function streamRegenerateAssistantReply(
  conversationId: number,
  assistantMessageId: number,
  body: ChatRegenerateBody | undefined,
  onPart: (p: StreamPart) => void,
): Promise<void> {
  const base = resolveApiBaseForBrowser();
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
  const res = await fetch(
    `${base}/open/v1/chat/conversations/${conversationId}/messages/${assistantMessageId}/retry`,
    {
      method: "POST",
      headers,
      body: JSON.stringify(body ?? {}),
    },
  );
  if (!res.ok || !res.body) {
    throw new Error("stream request failed: " + res.status);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
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
}

/** 使用 fetch 读取 SSE（携带 {@code X-Tenant-Id} 或 {@code X-Tenant-Code} 与 {@code X-Device-Id}）。data 行为 JSON 分帧：content / reasoning / ragDoc / webSearchRefs / end */
export async function streamAssistantReply(
  conversationId: number,
  payload: ChatSendPayload,
  onPart: (p: StreamPart) => void,
): Promise<void> {
  const base = resolveApiBaseForBrowser();
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
  const res = await fetch(`${base}/open/v1/chat/conversations/${conversationId}/messages`, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });
  if (!res.ok || !res.body) {
    throw new Error("stream request failed: " + res.status);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
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
}
