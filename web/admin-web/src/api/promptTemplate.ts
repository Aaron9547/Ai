import { http } from "@/plugins/http";

export type PromptTemplateKind = "SYSTEM" | "USER" | "FRAGMENT" | "QUERY";
export type PromptTemplateDomain =
  | "CHAT"
  | "MEMORY"
  | "RAG"
  | "WEB"
  | "PLANET"
  | "STARTER"
  | "GUARD";

export interface PromptTemplateRow {
  id: number;
  tenantId: number;
  platformDefault: boolean;
  promptCode: string;
  promptKind: PromptTemplateKind;
  domain: PromptTemplateDomain;
  locale: string;
  content: string;
  variablesSchemaJson: string | null;
  version: number;
  enabled: boolean;
  remark: string | null;
  sortOrder: number;
  updatedAt: string;
}

export interface PromptTemplateCreateBody {
  promptCode: string;
  promptKind: PromptTemplateKind;
  domain: PromptTemplateDomain;
  locale?: string;
  content: string;
  variablesSchemaJson?: string | null;
  version?: number;
  enabled?: boolean;
  remark?: string | null;
  sortOrder?: number;
}

export interface PromptTemplateUpdateBody {
  content?: string;
  variablesSchemaJson?: string | null;
  enabled?: boolean;
  remark?: string | null;
  sortOrder?: number;
}

export interface PromptTemplateCacheStats {
  redisEnabled: boolean;
  approximateKeyCount: number;
  hits: number;
  misses: number;
}

export async function listPromptTemplates(params?: {
  domain?: PromptTemplateDomain;
  promptKind?: PromptTemplateKind;
  locale?: string;
  enabled?: boolean;
}): Promise<PromptTemplateRow[]> {
  const { data } = await http.get<PromptTemplateRow[]>("/api/v1/admin/prompt-templates", { params });
  return data;
}

export async function createPromptTemplate(body: PromptTemplateCreateBody): Promise<PromptTemplateRow> {
  const { data } = await http.post<PromptTemplateRow>("/api/v1/admin/prompt-templates", body);
  return data;
}

export async function updatePromptTemplate(
  id: number,
  body: PromptTemplateUpdateBody,
): Promise<PromptTemplateRow> {
  const { data } = await http.put<PromptTemplateRow>(`/api/v1/admin/prompt-templates/${id}`, body);
  return data;
}

export async function deletePromptTemplate(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/prompt-templates/${id}`);
}

export async function fetchPromptTemplateCacheStats(): Promise<PromptTemplateCacheStats> {
  const { data } = await http.get<PromptTemplateCacheStats>("/api/v1/admin/prompt-templates/cache-stats");
  return data;
}

export async function evictPromptTemplateCache(body?: {
  tenantId?: number;
  promptCode?: string;
  locale?: string;
}): Promise<void> {
  await http.post("/api/v1/admin/prompt-templates/cache/evict", body ?? {});
}
