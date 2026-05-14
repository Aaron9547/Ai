import { http } from "../plugins/http";

export interface ChatConversationAdminPage {
  records: ChatConversationRow[];
  total: number;
  size: number;
  current: number;
}

export interface ChatConversationRow {
  id: number;
  tenantId: number;
  tenantCode?: string | null;
  tenantName?: string | null;
  userDisplayName?: string | null;
  /** 本会话助手消息（含历史稿）token 合计近似值，供列表抽检 */
  totalTokensInConversation?: number | null;
  userId: number | null;
  deviceId: string | null;
  title: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 与开放接口 {@code ChatMessageView.priorVersions} 项一致：重新生成前的助手快照 */
export interface PriorAssistantVersionAdmin {
  content?: string | null;
  reasoning?: string | null;
  modelAlias?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
}

/** 与助手消息 meta {@code ragCitations} 项一致 */
export interface RagCitationAdmin {
  kbId: number;
  documentId: number;
  documentTitle: string;
  chunkId: number;
  chunkSeq: number;
  contentPreview: string;
}

/** 与助手消息 meta {@code webSearchReferences} 项一致 */
export interface WebSearchRefAdmin {
  title: string;
  url: string;
  summary: string;
  siteName?: string | null;
  logoUrl?: string | null;
  publishTime?: string | null;
  extraJson?: string | null;
}

/** 用户消息随附的上传文件摘要（与 {@code ChatMessageView.attachments} 一致） */
export interface ChatAttachmentMessageAdmin {
  id: number;
  fileName: string;
  charLength: number | null;
}

export interface ChatMessageAdminRow {
  id: number;
  role: string;
  content: string;
  reasoning?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  createdAt?: string;
  modelAlias?: string | null;
  /** 曾重新生成时非空；当前正文仍为最新一版 */
  priorVersions?: PriorAssistantVersionAdmin[] | null;
  /** RAG 意图下命中的知识分片（可点击预览正文） */
  ragCitations?: RagCitationAdmin[] | null;
  /** 联网检索引用 */
  webSearchReferences?: WebSearchRefAdmin[] | null;
  /** 助手回复摘要（meta contentSummary），异步生成；无则 null */
  contentSummary?: string | null;
  /** 用户消息关联的上传附件 */
  attachments?: ChatAttachmentMessageAdmin[] | null;
}

export async function fetchChatConversations(
  page = 1,
  size = 20,
  opts?: { filterTenantId?: number },
): Promise<ChatConversationAdminPage> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) {
    params.filterTenantId = opts.filterTenantId;
  }
  const { data } = await http.get<ChatConversationAdminPage>("/api/v1/admin/chat/conversations", { params });
  return data;
}

export async function fetchChatMessages(conversationId: number): Promise<ChatMessageAdminRow[]> {
  const { data } = await http.get<ChatMessageAdminRow[]>(
    `/api/v1/admin/chat/conversations/${conversationId}/messages`,
  );
  return data;
}

export type SensitivePoolType = "PLATFORM" | "TENANT";

export interface SensitiveTermRow {
  id: number;
  poolType: SensitivePoolType;
  /** 扩展池所属租户编码（与数据租户下拉中括号内一致）；平台强制池为空 */
  tenantCode?: string | null;
  word: string;
  createdAt: string;
}

export interface SensitiveTermPage {
  records: SensitiveTermRow[];
  total: number;
  size: number;
  current: number;
}

export async function fetchSensitiveTermsPlatformPage(
  page = 1,
  size = 20,
  q?: string,
): Promise<SensitiveTermPage> {
  const params: Record<string, unknown> = { page, size };
  if (q != null && q.trim() !== "") {
    params.q = q.trim();
  }
  const { data } = await http.get<SensitiveTermPage>("/api/v1/admin/chat/sensitive-terms/platform", { params });
  return data;
}

export async function fetchSensitiveTermsTenantPage(
  page = 1,
  size = 20,
  opts?: { q?: string; filterTenantId?: number },
): Promise<SensitiveTermPage> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.q != null && opts.q.trim() !== "") {
    params.q = opts.q.trim();
  }
  if (opts?.filterTenantId != null) {
    params.filterTenantId = opts.filterTenantId;
  }
  const { data } = await http.get<SensitiveTermPage>("/api/v1/admin/chat/sensitive-terms/tenant", { params });
  return data;
}

export async function addSensitiveTerm(
  pool: SensitivePoolType,
  word: string,
  targetTenantId?: number,
): Promise<void> {
  const body: { pool: SensitivePoolType; word: string; targetTenantId?: number } = { pool, word };
  if (targetTenantId != null) {
    body.targetTenantId = targetTenantId;
  }
  await http.post("/api/v1/admin/chat/sensitive-terms", body);
}

export async function importSensitiveTerms(
  pool: SensitivePoolType,
  text: string,
  targetTenantId?: number,
): Promise<{
  inserted: number;
  skippedDuplicates: number;
  skippedInvalid: number;
}> {
  const body: { pool: SensitivePoolType; text: string; targetTenantId?: number } = { pool, text };
  if (targetTenantId != null) {
    body.targetTenantId = targetTenantId;
  }
  const { data } = await http.post<{
    inserted: number;
    skippedDuplicates: number;
    skippedInvalid: number;
  }>("/api/v1/admin/chat/sensitive-terms/import", body);
  return data;
}

export async function deleteSensitiveTerm(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/chat/sensitive-terms/${id}`);
}
