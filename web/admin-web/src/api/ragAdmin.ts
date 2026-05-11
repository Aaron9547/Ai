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

export async function enqueueUrlImportJob(
  id: number,
  url: string,
  chunkStrategy?: number,
): Promise<{ jobTaskId: number }> {
  const { data } = await http.post<{ jobTaskId: number }>(`${ragKbWithKbId(id)}/url-import-jobs`, {
    url,
    ...(chunkStrategy != null ? { chunkStrategy } : {}),
  });
  return data;
}

export async function enqueueFileIngestJob(
  id: number,
  body: { originalFilename: string; contentType?: string; markdownContent?: string; chunkStrategy?: number },
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

export async function fetchRagKbChunks(kbId: number, docId: number): Promise<RagChunkAdminRow[]> {
  const { data } = await http.get<RagChunkAdminRow[]>(`${ragKbWithKbId(kbId)}/documents/${docId}/chunks`);
  return data;
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

export async function uploadRagKbDocument(
  kbId: number,
  file: File,
  chunkStrategy?: number,
): Promise<{ documentId: number; chunkCount: number }> {
  const form = new FormData();
  form.append("file", file);
  if (chunkStrategy != null) {
    form.append("chunkStrategy", String(chunkStrategy));
  }
  const { data } = await http.post<{ documentId: number; chunkCount: number }>(
    `${ragKbWithKbId(kbId)}/documents/upload`,
    form,
  );
  return data;
}
