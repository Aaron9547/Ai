import { http } from "@/plugins/http";

export type ChatIntentHandlerKind = "TRAVEL_REIMBURSEMENT";
export type ToggleState = "OFF" | "ON";
export type ChatIntentKeywordKind = "TRIGGER" | "PLAN_CONTINUE";

export interface IntentRow {
  id: number;
  tenantId: number;
  code: string;
  displayName: string;
  description: string | null;
  handlerKind: ChatIntentHandlerKind;
  enabled: ToggleState;
  sortOrder: number;
  extraConfigJson: string | null;
}

export interface IntentCreateBody {
  code: string;
  displayName: string;
  description?: string | null;
  handlerKind: ChatIntentHandlerKind;
  enabled: ToggleState;
  sortOrder?: number | null;
  extraConfigJson?: string | null;
  targetTenantId?: number | null;
}

export interface IntentUpdateBody {
  displayName?: string | null;
  description?: string | null;
  handlerKind?: ChatIntentHandlerKind | null;
  enabled?: ToggleState | null;
  sortOrder?: number | null;
  extraConfigJson?: string | null;
  targetTenantId?: number | null;
}

export interface KeywordRow {
  id: number;
  intentId: number;
  phrase: string;
  keywordKind: ChatIntentKeywordKind;
  enabled: ToggleState;
  sortOrder: number;
}

export interface KeywordCreateBody {
  phrase: string;
  keywordKind: ChatIntentKeywordKind;
  enabled: ToggleState;
  sortOrder?: number | null;
}

export interface KeywordUpdateBody {
  phrase?: string | null;
  keywordKind?: ChatIntentKeywordKind | null;
  enabled?: ToggleState | null;
  sortOrder?: number | null;
}

export async function listIntents(filterTenantId?: number): Promise<IntentRow[]> {
  const { data } = await http.get<IntentRow[]>("/api/v1/admin/chat/intents", {
    params: filterTenantId != null ? { filterTenantId } : {},
  });
  return data;
}

export async function createIntent(body: IntentCreateBody): Promise<IntentRow> {
  const { data } = await http.post<IntentRow>("/api/v1/admin/chat/intents", body);
  return data;
}

export async function updateIntent(id: number, body: IntentUpdateBody): Promise<IntentRow> {
  const { data } = await http.put<IntentRow>(`/api/v1/admin/chat/intents/${id}`, body);
  return data;
}

export async function deleteIntent(id: number, targetTenantId?: number): Promise<void> {
  await http.delete(`/api/v1/admin/chat/intents/${id}`, {
    params: targetTenantId != null ? { targetTenantId } : {},
  });
}

export async function listKeywords(intentId: number, targetTenantId?: number): Promise<KeywordRow[]> {
  const { data } = await http.get<KeywordRow[]>(`/api/v1/admin/chat/intents/${intentId}/keywords`, {
    params: targetTenantId != null ? { targetTenantId } : {},
  });
  return data;
}

export async function addKeyword(
  intentId: number,
  body: KeywordCreateBody,
  targetTenantId?: number,
): Promise<KeywordRow> {
  const { data } = await http.post<KeywordRow>(`/api/v1/admin/chat/intents/${intentId}/keywords`, body, {
    params: targetTenantId != null ? { targetTenantId } : {},
  });
  return data;
}

export async function updateKeyword(
  intentId: number,
  keywordId: number,
  body: KeywordUpdateBody,
  targetTenantId?: number,
): Promise<KeywordRow> {
  const { data } = await http.put<KeywordRow>(
    `/api/v1/admin/chat/intents/${intentId}/keywords/${keywordId}`,
    body,
    { params: targetTenantId != null ? { targetTenantId } : {} },
  );
  return data;
}

export async function deleteKeyword(
  intentId: number,
  keywordId: number,
  targetTenantId?: number,
): Promise<void> {
  await http.delete(`/api/v1/admin/chat/intents/${intentId}/keywords/${keywordId}`, {
    params: targetTenantId != null ? { targetTenantId } : {},
  });
}
