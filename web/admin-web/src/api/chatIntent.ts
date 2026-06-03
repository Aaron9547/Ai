import { http } from "@/plugins/http";

/** 与后端 {@code ChatIntentHandlerKind} 枚举名一致；由 {@link listIntentHandlerKinds} 动态发现 */
export type ChatIntentHandlerKind = string;

export type IntentHandlerParamStorage = "HANDLER_PARAMS" | "TRAVEL_ROUTING";

export type IntentHandlerConfigValueKind =
  | "STRING"
  | "BOOLEAN"
  | "INT"
  | "SECRET_STRING"
  | "SELECT";

export interface IntentHandlerConfigOption {
  value: string;
  labelZh: string;
}

export interface IntentHandlerConfigFieldMeta {
  name: string;
  labelZh: string;
  valueKind: IntentHandlerConfigValueKind;
  required: boolean;
  sortOrder: number;
  placeholder: string | null;
  paramStorage?: IntentHandlerParamStorage | null;
  intMin?: number | null;
  intMax?: number | null;
  options?: IntentHandlerConfigOption[];
}

/** 管理端处理器下拉项（后端 {@code ChatIntentHandlerKind} + 已注册插件） */
export interface IntentHandlerKindOption {
  kind: string;
  labelZh: string;
  description: string;
}
export type ToggleState = "OFF" | "ON";
export type ChatIntentKeywordKind = "TRIGGER" | "PLAN_CONTINUE";

/** 管理端意图列表项（仅当前 JWT 工作区租户，响应不含 tenantId） */
export interface IntentRow {
  id: number;
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
}

export interface IntentUpdateBody {
  displayName?: string | null;
  description?: string | null;
  handlerKind?: ChatIntentHandlerKind | null;
  enabled?: ToggleState | null;
  sortOrder?: number | null;
  extraConfigJson?: string | null;
}

export interface KeywordRow {
  id: number;
  intentId: number;
  phrase: string;
  keywordKind: ChatIntentKeywordKind;
  /** 可选；处理器轮次名（如 DOC、PLAN） */
  targetRound?: string | null;
  enabled: ToggleState;
  sortOrder: number;
  /** 子串命中并进入意图 SSE 的累计次数（仅库内关键词 id 命中时递增） */
  hitCount: number;
}

export interface KeywordCreateBody {
  phrase: string;
  keywordKind: ChatIntentKeywordKind;
  targetRound?: string | null;
  enabled: ToggleState;
  sortOrder?: number | null;
}

export interface KeywordUpdateBody {
  phrase?: string | null;
  keywordKind?: ChatIntentKeywordKind | null;
  targetRound?: string | null;
  enabled?: ToggleState | null;
  sortOrder?: number | null;
}

export async function listIntents(): Promise<IntentRow[]> {
  const { data } = await http.get<IntentRow[]>("/api/v1/admin/chat/intents");
  return data;
}

export async function listIntentHandlerKinds(): Promise<IntentHandlerKindOption[]> {
  const { data } = await http.get<IntentHandlerKindOption[]>(
    "/api/v1/admin/chat/intent-handler-kinds",
  );
  return data;
}

export async function getIntentHandlerConfigSchema(
  kind: ChatIntentHandlerKind,
): Promise<IntentHandlerConfigFieldMeta[]> {
  const { data } = await http.get<IntentHandlerConfigFieldMeta[]>(
    `/api/v1/admin/chat/intent-handlers/${kind}/config-schema`,
  );
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

export async function deleteIntent(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/chat/intents/${id}`);
}

export async function listKeywords(intentId: number): Promise<KeywordRow[]> {
  const { data } = await http.get<KeywordRow[]>(`/api/v1/admin/chat/intents/${intentId}/keywords`);
  return data;
}

export async function addKeyword(intentId: number, body: KeywordCreateBody): Promise<KeywordRow> {
  const { data } = await http.post<KeywordRow>(`/api/v1/admin/chat/intents/${intentId}/keywords`, body);
  return data;
}

export async function updateKeyword(
  intentId: number,
  keywordId: number,
  body: KeywordUpdateBody,
): Promise<KeywordRow> {
  const { data } = await http.put<KeywordRow>(
    `/api/v1/admin/chat/intents/${intentId}/keywords/${keywordId}`,
    body,
  );
  return data;
}

export async function deleteKeyword(intentId: number, keywordId: number): Promise<void> {
  await http.delete(`/api/v1/admin/chat/intents/${intentId}/keywords/${keywordId}`);
}
