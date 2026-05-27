import { http } from "../plugins/http";

export interface StarterPromptRow {
  id: number;
  scene: string;
  source: string;
  promptText: string;
  weight: number;
  enabled: boolean;
  requireThinking?: boolean | null;
  requireWebSearch?: boolean | null;
  validFrom?: string | null;
  validUntil?: string | null;
  sortOrder: number;
  batchKey?: string | null;
  queryNormalized?: string | null;
  hitCount?: number;
  referenceCount?: number;
  groundingSummaryPreview?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface DailyBatchRow {
  id: number;
  topicDate: string;
  status: string;
  questions: string[];
  errorMessage?: string | null;
  fetchedAt?: string | null;
}

export interface StarterPromptPageResult {
  records: StarterPromptRow[];
  total: number;
  page: number;
  size: number;
}

export async function listStarterPrompts(params: {
  scene: string;
  page?: number;
  size?: number;
  source?: string;
}): Promise<StarterPromptPageResult> {
  const { data } = await http.get<StarterPromptPageResult>("/api/v1/admin/chat/starter-prompts", {
    params,
  });
  return data;
}

export async function createStarterPrompt(body: {
  scene: string;
  promptText: string;
  weight?: number;
  enabled?: boolean;
  requireThinking?: boolean;
  requireWebSearch?: boolean;
  validFrom?: string;
  validUntil?: string;
  sortOrder?: number;
}) {
  const { data } = await http.post<StarterPromptRow>("/api/v1/admin/chat/starter-prompts", body);
  return data;
}

export async function updateStarterPrompt(
  id: number,
  body: Partial<{
    promptText: string;
    weight: number;
    enabled: boolean;
    requireThinking: boolean;
    requireWebSearch: boolean;
    validFrom: string;
    validUntil: string;
    sortOrder: number;
  }>,
) {
  const { data } = await http.put<StarterPromptRow>(`/api/v1/admin/chat/starter-prompts/${id}`, body);
  return data;
}

export async function deleteStarterPrompt(id: number) {
  await http.delete(`/api/v1/admin/chat/starter-prompts/${id}`);
}

export interface WebGroundingReferenceItem {
  title: string;
  url: string;
  snippet: string;
  siteName?: string | null;
  publishTime?: string | null;
  /** 检索源码，如 BAIDU_NEWS_HTML、VOLCENGINE_ARK_BOT */
  sourceKey?: string | null;
}

export interface WebGroundingDetailView {
  promptText: string;
  queryNormalized: string;
  summaryText: string;
  references: WebGroundingReferenceItem[];
}

export async function getWebGroundingDetail(id: number) {
  const { data } = await http.get<WebGroundingDetailView>(
    `/api/v1/admin/chat/starter-prompts/${id}/web-grounding`,
  );
  return data;
}

export async function listDailyBatches(limit = 14) {
  const { data } = await http.get<DailyBatchRow[]>("/api/v1/admin/chat/starter-prompts/daily-batches", {
    params: { limit },
  });
  return data;
}

export async function refreshDailyHot(force = true) {
  const { data } = await http.post<{ ok: boolean; message: string; questionCount: number }>(
    "/api/v1/admin/chat/starter-prompts/refresh-daily-hot",
    null,
    { params: { force } },
  );
  return data;
}
