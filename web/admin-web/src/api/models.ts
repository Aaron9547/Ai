import { http } from "../plugins/http";

/** 与后端 `LlmModelKind` 一致；C 端对话仅 `LANGUAGE`。 */
export type LlmModelKindCode = "LANGUAGE" | "SPEECH" | "VISION" | "VECTOR" | "SMART_ROUTING";

/** 与后端 `LlmVectorBackend` 一致；仅 `VECTOR` 有效。 */
export type LlmVectorBackendCode =
  | "OPENAI_COMPATIBLE"
  | "VOLCENGINE_ARK"
  | "VOLCENGINE_ARK_MULTIMODAL";

/** 与后端 `LlmConnectorKind` 一致；元数据下发，持久化字段后续可扩展。 */
export type LlmConnectorKindCode = "OPENAI_COMPATIBLE_JSON";

export interface EnumOption {
  code: string;
  label: string;
  sortOrder: number;
}

export interface ShowUnless {
  field: string;
  equalsValue: unknown;
}

export interface FormFieldMeta {
  key: string;
  label: string;
  control: string;
  required: boolean;
  disabledOnEdit: boolean;
  placeholder: string | null;
  selectOptionsKey: string | null;
  showUnless?: ShowUnless | null;
}

export interface ListColumnMeta {
  prop: string;
  label: string;
  format: string;
  optionsKey?: string | null;
}

export interface ModelKindTabMeta {
  kind: LlmModelKindCode;
  label: string;
  sortOrder: number;
  listColumns: ListColumnMeta[];
  formFields: FormFieldMeta[];
}

export interface LlmModelAdminMetaResponse {
  modelKindTabs: ModelKindTabMeta[];
  optionLists: Record<string, EnumOption[]>;
}

export interface LlmModelAdminView {
  id: number;
  alias: string;
  displayName: string;
  openaiBaseUrl: string;
  openaiModelId: string;
  modelKind: LlmModelKindCode;
  /** 仅向量类型有值 */
  vectorBackend?: LlmVectorBackendCode | null;
  apiKeyConfigured: boolean;
  allowAnonymous: boolean;
  maxAttachments: number;
  supportsThinking: boolean;
  enabled: boolean;
  sortOrder: number;
  /** null = 不限制 */
  tokenQuotaTotal: number | null;
  tokensUsed: number;
  /** 本地部署：向量模型为 true 时经 Feign 调 RAG 网关 */
  localDeploy: boolean;
}

export interface CreateLlmModelBody {
  alias: string;
  displayName: string;
  openaiBaseUrl: string;
  openaiModelId: string;
  /** 省略时后端默认 `LANGUAGE` */
  modelKind?: LlmModelKindCode;
  /** 仅 `VECTOR`；省略时 `OPENAI_COMPATIBLE` */
  vectorBackend?: LlmVectorBackendCode;
  /** 向量模型可与内网免鉴权嵌入服务留空 */
  apiKey?: string;
  allowAnonymous: boolean;
  maxAttachments: number;
  supportsThinking: boolean;
  enabled: boolean;
  sortOrder?: number;
  /** null = 不限制 */
  tokenQuotaTotal?: number | null;
  /** 默认 false */
  localDeploy?: boolean;
}

export async function getLlmModelMeta(): Promise<LlmModelAdminMetaResponse> {
  const { data } = await http.get<LlmModelAdminMetaResponse>("/api/v1/admin/llm-models/meta");
  return data;
}

export async function listLlmModels(params?: { modelKind?: LlmModelKindCode }): Promise<LlmModelAdminView[]> {
  const { data } = await http.get<LlmModelAdminView[]>("/api/v1/admin/llm-models", { params });
  return data;
}

export async function createLlmModel(body: CreateLlmModelBody): Promise<LlmModelAdminView> {
  const { data } = await http.post<LlmModelAdminView>("/api/v1/admin/llm-models", body);
  return data;
}

export async function updateLlmModel(
  id: number,
  body: Partial<CreateLlmModelBody> & {
    modelKind?: LlmModelKindCode;
    vectorBackend?: LlmVectorBackendCode;
    apiKey?: string;
    /** true：清除已保存的 API Key（向量模型免鉴权等） */
    clearApiKey?: boolean;
    /** true：改为不限制额度 */
    tokenQuotaUnlimited?: boolean;
    tokenQuotaTotal?: number | null;
    localDeploy?: boolean;
  },
): Promise<LlmModelAdminView> {
  const { data } = await http.put<LlmModelAdminView>(`/api/v1/admin/llm-models/${id}`, body);
  return data;
}

export async function deleteLlmModel(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/llm-models/${id}`);
}
