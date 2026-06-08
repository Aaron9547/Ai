import { http } from "../plugins/http";
import type { RagCitationAdmin } from "./chatAdmin";

export interface AdminPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

export interface McpTraceRow {
  id: number;
  traceId: string;
  orchestrationTraceId?: string | null;
  httpTraceId?: string | null;
  tenantId: number;
  userId?: number | null;
  conversationId?: number | null;
  conversationPublicId?: string | null;
  sourceScene?: string | null;
  llmRound?: number | null;
  qualifiedToolName?: string | null;
  serverId?: number | null;
  success?: boolean | null;
  errorCode?: string | null;
  latencyMs?: number | null;
  argumentsJson?: string | null;
  resultJson?: string | null;
  createdAt?: string | null;
  conversationTitle?: string | null;
}

export interface RagHitRow {
  id: number;
  hitTraceId: string;
  httpTraceId?: string | null;
  tenantId: number;
  userId?: number | null;
  conversationId?: number | null;
  userMessageId?: number | null;
  assistantMessageId?: number | null;
  kbId?: number | null;
  documentId?: number | null;
  chunkId?: number | null;
  chunkSeq?: number | null;
  retrievalMode?: string | null;
  hitSource?: string | null;
  vectorSimilarity?: number | null;
  keywordScore?: number | null;
  queryText?: string | null;
  rankInBatch?: number | null;
  createdAt?: string | null;
  conversationTitle?: string | null;
  userQuestionPreview?: string | null;
  kbName?: string | null;
  documentTitle?: string | null;
  chunkLabel?: string | null;
}

export type RagQualityAssessmentScope = "MESSAGE_TURN" | "CHUNK_QUERY";
export type EvalRunStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface RagQualityAssessmentSubmitRequest {
  scope: RagQualityAssessmentScope;
  conversationId?: number | null;
  userMessageId?: number | null;
  assistantMessageId?: number | null;
  kbId?: number | null;
  chunkId?: number | null;
  queryText?: string | null;
  assistantAnswer?: string | null;
  citations?: RagCitationAdmin[] | null;
}

export interface RagQualityAssessmentView {
  runId: string;
  scope: RagQualityAssessmentScope;
  status: EvalRunStatus;
  conversationId?: number | null;
  userMessageId?: number | null;
  assistantMessageId?: number | null;
  kbId?: number | null;
  chunkId?: number | null;
  queryText?: string | null;
  recallHitRate?: number | null;
  citationAccuracy?: number | null;
  faithfulnessScore?: number | null;
  resultJson?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  conversationTitle?: string | null;
  kbName?: string | null;
  chunkLabel?: string | null;
}

export interface RagQualityAssessmentListRow {
  id: number;
  runId: string;
  tenantId: number;
  scope: RagQualityAssessmentScope;
  status: EvalRunStatus;
  conversationId?: number | null;
  userMessageId?: number | null;
  assistantMessageId?: number | null;
  kbId?: number | null;
  chunkId?: number | null;
  queryText?: string | null;
  recallHitRate?: number | null;
  citationAccuracy?: number | null;
  faithfulnessScore?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  conversationTitle?: string | null;
  kbName?: string | null;
  chunkLabel?: string | null;
}

export interface ConversationTracesView {
  mcpTraces: McpTraceRow[];
  ragHits: RagHitRow[];
}

export type McpTraceListOpts = {
  filterTenantId?: number;
  conversationId?: number;
  conversationKeyword?: string;
  toolName?: string;
  success?: boolean;
  days?: number;
};

export type RagHitListOpts = {
  filterTenantId?: number;
  conversationId?: number;
  conversationKeyword?: string;
  queryKeyword?: string;
  kbId?: number;
  kbNameKeyword?: string;
  chunkId?: number;
  days?: number;
};

export type RagQualityListOpts = {
  filterTenantId?: number;
  scope?: RagQualityAssessmentScope;
  conversationKeyword?: string;
  queryKeyword?: string;
  days?: number;
};

export async function fetchMcpTraces(
  page = 1,
  size = 20,
  opts?: McpTraceListOpts,
): Promise<AdminPage<McpTraceRow>> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) params.filterTenantId = opts.filterTenantId;
  if (opts?.conversationId != null) params.conversationId = opts.conversationId;
  if (opts?.conversationKeyword != null && opts.conversationKeyword !== "") {
    params.conversationKeyword = opts.conversationKeyword;
  }
  if (opts?.toolName != null && opts.toolName !== "") params.toolName = opts.toolName;
  if (opts?.success != null) params.success = opts.success;
  if (opts?.days != null) params.days = opts.days;
  const { data } = await http.get<AdminPage<McpTraceRow>>("/api/v1/admin/observability/mcp-traces", { params });
  return data;
}

export async function fetchMcpTraceDetail(traceId: string): Promise<McpTraceRow> {
  const { data } = await http.get<McpTraceRow>(`/api/v1/admin/observability/mcp-traces/${encodeURIComponent(traceId)}`);
  return data;
}

export async function fetchRagHits(
  page = 1,
  size = 20,
  opts?: RagHitListOpts,
): Promise<AdminPage<RagHitRow>> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) params.filterTenantId = opts.filterTenantId;
  if (opts?.conversationId != null) params.conversationId = opts.conversationId;
  if (opts?.conversationKeyword != null && opts.conversationKeyword !== "") {
    params.conversationKeyword = opts.conversationKeyword;
  }
  if (opts?.queryKeyword != null && opts.queryKeyword !== "") params.queryKeyword = opts.queryKeyword;
  if (opts?.kbId != null) params.kbId = opts.kbId;
  if (opts?.kbNameKeyword != null && opts.kbNameKeyword !== "") params.kbNameKeyword = opts.kbNameKeyword;
  if (opts?.chunkId != null) params.chunkId = opts.chunkId;
  if (opts?.days != null) params.days = opts.days;
  const { data } = await http.get<AdminPage<RagHitRow>>("/api/v1/admin/observability/rag-hits", { params });
  return data;
}

export async function fetchRagHitBatch(hitTraceId: string): Promise<RagHitRow[]> {
  const { data } = await http.get<RagHitRow[]>(
    `/api/v1/admin/observability/rag-hits/${encodeURIComponent(hitTraceId)}`,
  );
  return data ?? [];
}

export async function fetchConversationTraces(conversationId: number): Promise<ConversationTracesView> {
  const { data } = await http.get<ConversationTracesView>(
    `/api/v1/admin/observability/conversations/${conversationId}/traces`,
  );
  return data;
}

export async function submitRagQualityAssessment(
  req: RagQualityAssessmentSubmitRequest,
): Promise<RagQualityAssessmentView> {
  const { data } = await http.post<RagQualityAssessmentView>("/api/v1/admin/rag-quality/assessments", req);
  return data;
}

export async function fetchRagQualityAssessment(runId: string): Promise<RagQualityAssessmentView> {
  const { data } = await http.get<RagQualityAssessmentView>(
    `/api/v1/admin/rag-quality/assessments/${encodeURIComponent(runId)}`,
  );
  return data;
}

export async function fetchRagQualityAssessments(
  page = 1,
  size = 20,
  opts?: RagQualityListOpts,
): Promise<AdminPage<RagQualityAssessmentListRow>> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) params.filterTenantId = opts.filterTenantId;
  if (opts?.scope != null) params.scope = opts.scope;
  if (opts?.conversationKeyword != null && opts.conversationKeyword !== "") {
    params.conversationKeyword = opts.conversationKeyword;
  }
  if (opts?.queryKeyword != null && opts.queryKeyword !== "") params.queryKeyword = opts.queryKeyword;
  if (opts?.days != null) params.days = opts.days;
  const { data } = await http.get<AdminPage<RagQualityAssessmentListRow>>(
    "/api/v1/admin/rag-quality/assessments",
    { params },
  );
  return data;
}
