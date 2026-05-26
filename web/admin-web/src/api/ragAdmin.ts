import { http, AI_ADMIN_ACCESS_TOKEN_KEY, AI_ADMIN_MEMBERSHIPS_KEY } from "../plugins/http";
import { readJwtTid } from "@/utils/jwtSubject";
import type {
  MybatisPage,
  RagChunkAdminRow,
  RagDocumentAdminRow,
  RagDocumentCategoryAdminRow,
  RagKnowledgeBaseRow,
} from "../types/admin";

/** URL 路径段：与后端 {@code /admin/rag-kbs/{tenantCode}} 对齐，取自 JWT tid + memberships，或 {@code VITE_TENANT_CODE}。 */
function encodeTenantCodePathSegment(raw: string): string {
  return encodeURIComponent(raw.trim());
}

/**
 * 解析当前管理端工作区对应的 {@code sys_tenant.code}（用于 RAG 管理 API 首段路径）。
 * 须与请求头 {@code X-Tenant-Id}（JWT {@code tid}）指向的 memberships 行一致。
 */
export function resolveAdminRagKbTenantPathSegment(): string {
  const token =
    typeof localStorage !== "undefined" ? localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY) : null;
  const tidStr = readJwtTid(token);
  const tid =
    tidStr && /^\d+$/.test(tidStr.trim())
      ? Number.parseInt(tidStr.trim(), 10)
      : Number.parseInt(String(import.meta.env.VITE_TENANT_ID ?? "1"), 10);
  const raw =
    typeof localStorage !== "undefined" ? localStorage.getItem(AI_ADMIN_MEMBERSHIPS_KEY) : null;
  if (raw) {
    try {
      const arr = JSON.parse(raw) as { tenantId: number; tenantCode?: string }[];
      if (Array.isArray(arr)) {
        const m = arr.find((x) => Number(x.tenantId) === tid);
        if (m?.tenantCode && String(m.tenantCode).trim()) {
          return encodeTenantCodePathSegment(String(m.tenantCode));
        }
      }
    } catch {
      /* ignore */
    }
  }
  const fb = import.meta.env.VITE_TENANT_CODE;
  if (fb != null && String(fb).trim()) {
    return encodeTenantCodePathSegment(String(fb));
  }
  return "default";
}

function ragKbBase(): string {
  return `/api/v1/admin/rag-kbs/${resolveAdminRagKbTenantPathSegment()}`;
}

function ragKbWithKbId(kbId: number): string {
  return `${ragKbBase()}/${kbId}`;
}

export async function fetchRagKbs(): Promise<RagKnowledgeBaseRow[]> {
  const { data } = await http.get<RagKnowledgeBaseRow[]>(ragKbBase());
  return data;
}

/** 与后端 {@code GET .../rag-kbs/{tenantCode}/capabilities} 对齐：是否已接入 Milvus。 */
export async function fetchRagCapabilities(): Promise<{ vectorStoreMilvus: boolean }> {
  const { data } = await http.get<{ vectorStoreMilvus: boolean }>(`${ragKbBase()}/capabilities`);
  return data;
}

export async function createRagKb(name: string): Promise<RagKnowledgeBaseRow> {
  const { data } = await http.post<RagKnowledgeBaseRow>(ragKbBase(), { name });
  return data;
}

export async function updateRagKb(id: number, name: string): Promise<RagKnowledgeBaseRow> {
  const { data } = await http.put<RagKnowledgeBaseRow>(`${ragKbWithKbId(id)}`, { name });
  return data;
}

export async function patchRagKbSettings(
  id: number,
  body: {
    name?: string;
    defaultChunkStrategyCode?: number;
    chunkFixedChars?: number;
    chunkSlideOverlap?: number;
    assignedLlmModelId?: number | null;
    clearAssignedLlmModel?: boolean;
    assignedEmbeddingModelId?: number | null;
    clearAssignedEmbeddingModel?: boolean;
    chatRetrievalEnabled?: boolean;
    chatVectorMinCosineScore?: number;
  },
): Promise<RagKnowledgeBaseRow> {
  const { data } = await http.patch<RagKnowledgeBaseRow>(`${ragKbWithKbId(id)}/settings`, body);
  return data;
}

export async function deleteRagKb(id: number): Promise<void> {
  await http.delete(`${ragKbWithKbId(id)}`);
}

export async function enqueueRagKbIndexJob(id: number): Promise<{ jobTaskId: number }> {
  const { data } = await http.post<{ jobTaskId: number }>(`${ragKbWithKbId(id)}/index-jobs`);
  return data;
}

export type RagRetrievalTestHit = {
  documentId: number;
  documentTitle: string;
  chunkId: number;
  chunkSeq: number;
  contentPreview: string;
  hitSource?: "milvus" | "es" | null;
  vectorSimilarity?: number | null;
  keywordScore?: number | null;
};

export type RagRetrievalTestResult = {
  retrievalMode: string;
  query: string;
  topK: number;
  hitCount: number;
  hits: RagRetrievalTestHit[];
  snippets: string[];
  milvusRecallCount?: number;
  afterCosineThresholdCount?: number;
  minCosineThreshold?: number;
  maxMilvusSimilarity?: number;
  diagnosticsHint?: string;
};

/** 与后端 {@code POST .../rag-kbs/{tenantCode}/{id}/retrieval-test} 对齐。 */
export async function testRagKbRetrieval(
  kbId: number,
  body: { query: string; topK?: number },
): Promise<RagRetrievalTestResult> {
  const { data } = await http.post<RagRetrievalTestResult>(`${ragKbWithKbId(kbId)}/retrieval-test`, body);
  return data;
}

export type RagWebCrawlJsRenderOverride = {
  enabled?: boolean;
  maxPagesPerRun?: number;
  onlyWhenLinkCountBelow?: number;
};

export type RagWebCrawlDiscoveryOverride = {
  strategies?: string[];
  maxDepth?: number;
  jsRender?: RagWebCrawlJsRenderOverride;
};

export type RagWebCrawlPolitenessOverride = {
  perHostQps?: number;
  perHostConcurrency?: number;
  globalConcurrency?: number;
};

export type RagWebCrawlExtractConfig = {
  extractor?: string;
  contentSelector?: string;
  excludeSelectors?: string[];
  titleSelector?: string;
  presetLock?: string;
  discovery?: RagWebCrawlDiscoveryOverride;
  politeness?: RagWebCrawlPolitenessOverride;
};

export type ChunkPreviewRequestBody = {
  url?: string;
  baseUrl?: string;
  maxDepth?: number;
  chunkStrategy?: number;
  siteId?: number;
  extractConfig?: RagWebCrawlExtractConfig;
};

export type ChunkPreviewChunkRow = { seq: number; chars: number; preview: string };

export type ChunkPreviewPageRow = {
  url: string;
  title: string;
  markdownChars: number;
  chunkCount: number;
  chunksTruncated: boolean;
  chunks: ChunkPreviewChunkRow[];
  error?: string | null;
};

export type ChunkPreviewResult = {
  strategyCode: number;
  fixedChars: number;
  slideOverlap: number;
  pages: ChunkPreviewPageRow[];
  pageCount: number;
  totalChunkCount: number;
  discoveredUrlCount?: number;
};

export async function previewIngestChunks(kbId: number, body: ChunkPreviewRequestBody): Promise<ChunkPreviewResult> {
  const { data } = await http.post<ChunkPreviewResult>(`${ragKbWithKbId(kbId)}/ingest/preview-chunks`, body);
  return data;
}

export async function enqueueUrlImportJob(
  id: number,
  url: string,
  chunkStrategy?: number,
  categoryId?: number,
): Promise<{ jobTaskId: number }> {
  const { data } = await http.post<{ jobTaskId: number }>(`${ragKbWithKbId(id)}/url-import-jobs`, {
    url,
    ...(chunkStrategy != null ? { chunkStrategy } : {}),
    ...(categoryId != null ? { categoryId } : {}),
  });
  return data;
}

export async function enqueueFileIngestJob(
  id: number,
  body: {
    originalFilename: string;
    contentType?: string;
    markdownContent?: string;
    chunkStrategy?: number;
    categoryId?: number;
  },
): Promise<{ jobTaskId: number }> {
  const { data } = await http.post<{ jobTaskId: number }>(`${ragKbWithKbId(id)}/file-ingest-jobs`, body);
  return data;
}

export async function fetchRagKbDocuments(kbId: number): Promise<RagDocumentAdminRow[]> {
  const { data } = await http.get<RagDocumentAdminRow[]>(`${ragKbWithKbId(kbId)}/documents`);
  return data;
}

export async function fetchRagKbDocumentsPage(
  kbId: number,
  params: {
    page?: number;
    size?: number;
    categoryId?: number;
    displayStatus?: string;
    titleKeyword?: string;
  },
): Promise<MybatisPage<RagDocumentAdminRow>> {
  const { data } = await http.get<MybatisPage<RagDocumentAdminRow>>(`${ragKbWithKbId(kbId)}/documents/page`, {
    params: {
      page: params.page ?? 1,
      size: params.size ?? 10,
      ...(params.categoryId != null ? { categoryId: params.categoryId } : {}),
      ...(params.displayStatus ? { displayStatus: params.displayStatus } : {}),
      ...(params.titleKeyword ? { titleKeyword: params.titleKeyword } : {}),
    },
  });
  return data;
}

export async function fetchRagKbDocumentCategories(kbId: number): Promise<RagDocumentCategoryAdminRow[]> {
  const { data } = await http.get<RagDocumentCategoryAdminRow[]>(
    `${ragKbWithKbId(kbId)}/document-categories`,
  );
  return data;
}

export async function createRagKbDocumentCategory(
  kbId: number,
  body: { name: string; sortOrder?: number },
): Promise<RagDocumentCategoryAdminRow> {
  const { data } = await http.post<RagDocumentCategoryAdminRow>(
    `${ragKbWithKbId(kbId)}/document-categories`,
    body,
  );
  return data;
}

export async function updateRagKbDocumentCategory(
  kbId: number,
  catId: number,
  body: { name: string; sortOrder?: number },
): Promise<RagDocumentCategoryAdminRow> {
  const { data } = await http.put<RagDocumentCategoryAdminRow>(
    `${ragKbWithKbId(kbId)}/document-categories/${catId}`,
    body,
  );
  return data;
}

export async function deleteRagKbDocumentCategory(kbId: number, catId: number): Promise<void> {
  await http.delete(`${ragKbWithKbId(kbId)}/document-categories/${catId}`);
}

export async function patchRagKbDocument(
  kbId: number,
  docId: number,
  body: {
    categoryId?: number | null;
    clearCategory?: boolean;
    displayStatus?: string;
    applicableScope?: string | null;
  },
): Promise<RagDocumentAdminRow> {
  const { data } = await http.patch<RagDocumentAdminRow>(`${ragKbWithKbId(kbId)}/documents/${docId}`, body);
  return data;
}

export async function exportRagKbDocumentMarkdown(kbId: number, docId: number): Promise<string> {
  const { data } = await http.get<string>(`${ragKbWithKbId(kbId)}/documents/${docId}/markdown`, {
    responseType: "text",
    transformResponse: [(body) => body],
  });
  return typeof data === "string" ? data : String(data ?? "");
}

export async function deleteRagKbDocument(kbId: number, docId: number): Promise<void> {
  await http.delete(`${ragKbWithKbId(kbId)}/documents/${docId}`);
}

export async function fetchRagKbDocument(kbId: number, docId: number): Promise<RagDocumentAdminRow> {
  const { data } = await http.get<RagDocumentAdminRow>(`${ragKbWithKbId(kbId)}/document/${docId}`);
  return data;
}

/** 文档分片列表（含子母结构统计）。兼容旧版直接返回数组的接口。 */
export interface RagDocumentChunksListResponse {
  chunks: RagChunkAdminRow[];
  parentChild: boolean;
  parentCount: number;
  childCount: number;
  flatCount: number;
}

function normalizeChunkRow(raw: Record<string, unknown>): RagChunkAdminRow {
  const parentChunkId = raw.parentChunkId ?? raw.parent_chunk_id;
  const chunkRole = raw.chunkRole ?? raw.chunk_role;
  const retrievalEnabled = raw.retrievalEnabled ?? raw.retrieval_enabled;
  return {
    id: Number(raw.id),
    seq: Number(raw.seq ?? 0),
    content: String(raw.content ?? ""),
    embeddingRef: (raw.embeddingRef ?? raw.embedding_ref) as string | null | undefined,
    retrievalEnabled: retrievalEnabled as string | null | undefined,
    contentLength: raw.contentLength != null ? Number(raw.contentLength) : raw.content_length != null ? Number(raw.content_length) : undefined,
    hitCount: raw.hitCount != null ? Number(raw.hitCount) : raw.hit_count != null ? Number(raw.hit_count) : undefined,
    parentChunkId: parentChunkId != null ? Number(parentChunkId) : null,
    chunkRole: chunkRole != null ? String(chunkRole) : null,
    createdAt: (raw.createdAt ?? raw.created_at) as string | null | undefined,
    updatedAt: (raw.updatedAt ?? raw.updated_at) as string | null | undefined,
  };
}

function inferParentChildFromChunks(chunks: RagChunkAdminRow[]): Pick<RagDocumentChunksListResponse, "parentChild" | "parentCount" | "childCount" | "flatCount"> {
  let parents = 0;
  let children = 0;
  let flat = 0;
  const parentIdSet = new Set<number>();
  for (const c of chunks) {
    if (c.parentChunkId != null) parentIdSet.add(c.parentChunkId);
  }
  for (const c of chunks) {
    const role = c.chunkRole;
    if (role === "PARENT") parents += 1;
    else if (role === "CHILD") children += 1;
    else if (c.parentChunkId != null) children += 1;
    else if (parentIdSet.has(c.id)) parents += 1;
    else if (c.retrievalEnabled === "DISABLED" && parentIdSet.size > 0) parents += 1;
    else flat += 1;
  }
  const parentChild = children > 0 || parents > 0 || parentIdSet.size > 0;
  return { parentChild, parentCount: parents, childCount: children, flatCount: flat };
}

function normalizeChunksListResponse(data: unknown): RagDocumentChunksListResponse {
  if (Array.isArray(data)) {
    const chunks = data.map((row) => normalizeChunkRow(row as Record<string, unknown>));
    return { chunks, ...inferParentChildFromChunks(chunks) };
  }
  const o = (data ?? {}) as Record<string, unknown>;
  const rawChunks = Array.isArray(o.chunks) ? o.chunks : [];
  const chunks = rawChunks.map((row) => normalizeChunkRow(row as Record<string, unknown>));
  const inferred = inferParentChildFromChunks(chunks);
  return {
    chunks,
    parentChild: o.parentChild === true || o.parent_child === true || inferred.parentChild,
    parentCount: o.parentCount != null ? Number(o.parentCount) : o.parent_count != null ? Number(o.parent_count) : inferred.parentCount,
    childCount: o.childCount != null ? Number(o.childCount) : o.child_count != null ? Number(o.child_count) : inferred.childCount,
    flatCount: o.flatCount != null ? Number(o.flatCount) : o.flat_count != null ? Number(o.flat_count) : inferred.flatCount,
  };
}

export async function fetchRagKbChunks(kbId: number, docId: number): Promise<RagDocumentChunksListResponse> {
  const { data } = await http.get<unknown>(`${ragKbWithKbId(kbId)}/documents/${docId}/chunks`);
  return normalizeChunksListResponse(data);
}

export async function patchRagKbChunk(
  kbId: number,
  docId: number,
  chunkId: number,
  body: { content?: string; retrievalEnabled?: string },
): Promise<RagChunkAdminRow> {
  const { data } = await http.patch<RagChunkAdminRow>(
    `${ragKbWithKbId(kbId)}/documents/${docId}/chunks/${chunkId}`,
    body,
  );
  return data;
}

export async function deleteRagKbChunk(kbId: number, docId: number, chunkId: number): Promise<void> {
  await http.delete(`${ragKbWithKbId(kbId)}/documents/${docId}/chunks/${chunkId}`);
}

export async function createRagKbChunk(kbId: number, docId: number, content: string): Promise<RagChunkAdminRow> {
  const { data } = await http.post<RagChunkAdminRow>(`${ragKbWithKbId(kbId)}/documents/${docId}/chunks`, {
    content,
  });
  return data;
}

export async function mergeRagKbChunkWithNext(
  kbId: number,
  docId: number,
  chunkId: number,
): Promise<RagChunkAdminRow> {
  const { data } = await http.post<RagChunkAdminRow>(
    `${ragKbWithKbId(kbId)}/documents/${docId}/chunks/${chunkId}/merge-with-next`,
  );
  return data;
}

export interface RagWebCrawlSyncModeOption {
  code: string;
  label: string;
}

export interface RagWebCrawlSiteRow {
  id: number;
  kbId: number;
  name: string;
  baseUrl: string;
  schedulePreset: string;
  schedulePresetLabel: string;
  runAtTime?: string | null;
  enabled: boolean;
  firstRunDone: boolean;
  lastCrawlAt?: string | null;
  categoryId?: number | null;
  chunkStrategy?: number | null;
  syncMode?: string | null;
  syncModeLabel?: string | null;
  maxDepth?: number | null;
  filterCrawled: boolean;
  extractConfig?: RagWebCrawlExtractConfig | null;
}

export interface RagWebCrawlSiteMeta {
  syncModes: RagWebCrawlSyncModeOption[];
  schedulePresets: { code: string; label: string; intervalDays?: number }[];
  contentExtractors?: { code: string; label: string }[];
  tenantSiteCrawlPreset?: string;
  tenantSiteCrawlPolicySummary?: string;
}

export interface CrawlRunSummaryView {
  runId: number;
  kbId: number;
  siteId?: number | null;
  baseUrl: string;
  syncMode: string;
  status: string;
  preset?: string | null;
  createdAt?: string | null;
}

export interface CrawlRunDetailView {
  runId: number;
  kbId: number;
  siteId?: number | null;
  baseUrl: string;
  syncMode: string;
  status: string;
  preset?: string | null;
  policySummary?: string | null;
  statsJson?: string | null;
  queueByStatus: Record<string, number>;
  failedByCode: Record<string, number>;
  discoveryByStrategy: Record<string, number>;
  ok: number;
  skipped: number;
  fail: number;
}

export async function fetchCrawlRuns(
  kbId: number,
  opts?: { limit?: number; siteId?: number },
): Promise<CrawlRunSummaryView[]> {
  const { data } = await http.get<CrawlRunSummaryView[]>(`${ragKbWithKbId(kbId)}/crawl-runs`, {
    params: { limit: opts?.limit ?? 20, siteId: opts?.siteId },
  });
  return data;
}

export async function fetchWebCrawlSiteMeta(): Promise<RagWebCrawlSiteMeta> {
  const { data } = await http.get<RagWebCrawlSiteMeta>(`${ragKbBase()}/web-crawl/site-meta`);
  return data;
}

export async function fetchCrawlRun(kbId: number, runId: number): Promise<CrawlRunDetailView> {
  const { data } = await http.get<CrawlRunDetailView>(`${ragKbWithKbId(kbId)}/crawl-runs/${runId}`);
  return data;
}

export async function resumeCrawlRunPending(
  kbId: number,
  runId: number,
): Promise<{ runId: number; executed: boolean; ok: number; skipped: number; fail: number; summary: string }> {
  const { data } = await http.post(`${ragKbWithKbId(kbId)}/crawl-runs/${runId}/resume-pending`);
  return data;
}

export async function retryCrawlRunFailed(
  kbId: number,
  runId: number,
): Promise<{ runId: number; executed: boolean; ok: number; skipped: number; fail: number; summary: string }> {
  const { data } = await http.post(`${ragKbWithKbId(kbId)}/crawl-runs/${runId}/retry-failed`);
  return data;
}

/** @deprecated use fetchWebCrawlSiteMeta */
export async function fetchWebCrawlSyncModes(): Promise<RagWebCrawlSyncModeOption[]> {
  const meta = await fetchWebCrawlSiteMeta();
  return meta.syncModes ?? [];
}

export async function fetchWebCrawlSites(kbId: number): Promise<RagWebCrawlSiteRow[]> {
  const { data } = await http.get<RagWebCrawlSiteRow[]>(`${ragKbWithKbId(kbId)}/web-crawl/sites`);
  return data;
}

export async function createWebCrawlSite(
  kbId: number,
  body: {
    name: string;
    baseUrl: string;
    schedulePreset: string;
    runAtTime?: string;
    enabled?: boolean;
    syncMode?: string;
    maxDepth?: number;
    filterCrawled?: boolean;
    categoryId?: number;
    chunkStrategy?: number;
    extractConfig?: RagWebCrawlExtractConfig;
  },
): Promise<RagWebCrawlSiteRow> {
  const { data } = await http.post<RagWebCrawlSiteRow>(`${ragKbWithKbId(kbId)}/web-crawl/sites`, body);
  return data;
}

export async function updateWebCrawlSite(
  kbId: number,
  siteId: number,
  body: Partial<{
    name: string;
    baseUrl: string;
    schedulePreset: string;
    runAtTime: string;
    enabled: boolean;
    syncMode: string;
    maxDepth: number;
    filterCrawled: boolean;
    categoryId: number;
    chunkStrategy: number;
    extractConfig: RagWebCrawlExtractConfig;
  }>,
): Promise<RagWebCrawlSiteRow> {
  const { data } = await http.put<RagWebCrawlSiteRow>(`${ragKbWithKbId(kbId)}/web-crawl/sites/${siteId}`, body);
  return data;
}

export async function deleteWebCrawlSite(
  kbId: number,
  siteId: number,
  purgeDocuments: boolean,
): Promise<{ deleted: boolean; purgeDocuments: boolean; documentsPurged: number; crawlRunsRemoved: number }> {
  const { data } = await http.delete(`${ragKbWithKbId(kbId)}/web-crawl/sites/${siteId}`, {
    params: { purgeDocuments },
  });
  return data;
}

export async function runWebCrawlSiteNow(kbId: number, siteId: number): Promise<void> {
  await http.post(`${ragKbWithKbId(kbId)}/web-crawl/sites/${siteId}/run`);
}

export async function submitLocalSiteCrawl(
  kbId: number,
  body: {
    baseUrl: string;
    syncMode?: string;
    maxDepth?: number;
    filterCrawled?: boolean;
    chunkStrategy?: number;
    categoryId?: number;
    asyncJob?: boolean;
  },
): Promise<{ accepted: boolean }> {
  const { data } = await http.post<{ accepted: boolean }>(`${ragKbWithKbId(kbId)}/web-crawl/local`, body);
  return data;
}

export type RagIngestAnalyzeResult = {
  suggestParentChild: boolean;
  charCount: number;
  majorHeadingCount: number;
  minorHeadingCount: number;
  reasons: string[];
};

/** 子母分片策略 code，与后端 {@link RagChunkStrategy#PARENT_CHILD} 一致。 */
export const RAG_CHUNK_STRATEGY_PARENT_CHILD = 4;

export async function analyzeIngestUpload(kbId: number, file: File): Promise<RagIngestAnalyzeResult> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await http.post<RagIngestAnalyzeResult>(
    `${ragKbWithKbId(kbId)}/ingest/analyze-upload`,
    form,
  );
  return data;
}

export async function analyzeIngestMarkdown(
  kbId: number,
  markdownContent: string,
): Promise<RagIngestAnalyzeResult> {
  const { data } = await http.post<RagIngestAnalyzeResult>(`${ragKbWithKbId(kbId)}/ingest/analyze`, {
    markdownContent,
  });
  return data;
}

export async function uploadRagKbDocument(
  kbId: number,
  file: File,
  chunkStrategy?: number,
  categoryId?: number,
): Promise<{ documentId: number; chunkCount: number }> {
  const form = new FormData();
  form.append("file", file);
  if (chunkStrategy != null) {
    form.append("chunkStrategy", String(chunkStrategy));
  }
  if (categoryId != null) {
    form.append("categoryId", String(categoryId));
  }
  const { data } = await http.post<{ documentId: number; chunkCount: number }>(
    `${ragKbWithKbId(kbId)}/documents/upload`,
    form,
  );
  return data;
}
