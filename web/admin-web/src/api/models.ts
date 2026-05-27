import { http } from "../plugins/http";
import { adminUiNegotiationHeaders, adminUiNegotiationParams, type AdminUiLocaleTag } from "./adminUiNegotiation";

/** 与后端 `LlmModelKind` 一致；C 端对话仅 `LANGUAGE`。 */
export type LlmModelKindCode = "LANGUAGE" | "SPEECH" | "VISION" | "VECTOR" | "SMART_ROUTING" | "WEB_SEARCH";

/** 与后端 `LlmWebSearchProvider` 一致；写入 {@code llm_model.integration_backend}（WEB_SEARCH 行）。 */
/** 仅火山 Ark；内置固定源见 {@link WebSearchFixedSourceCode}，不在模型表配置。 */
export type LlmWebSearchProviderCode = "VOLCENGINE_ARK_BOT";

/** 与后端 {@code WebSearchFixedSource} 一致；租户 Shell 勾选，非 llm_model 行。 */
export type WebSearchFixedSourceCode =
  | "DUCKDUCKGO_HTML"
  | "WIKIPEDIA_REST"
  | "GOOGLE_NEWS_RSS"
  | "BAIDU_NEWS_HTML";

/** 与后端 `LlmVectorBackend` 一致；写入 {@code llm_model.integration_backend}（VECTOR 行）。 */
export type LlmVectorBackendCode =
  | "OPENAI_COMPATIBLE"
  | "VOLCENGINE_ARK"
  | "VOLCENGINE_ARK_MULTIMODAL"
  | "DASHSCOPE_COMPATIBLE"
  | "DASHSCOPE_TEXT_EMBEDDING"
  | "DASHSCOPE_MULTIMODAL_EMBEDDING";

/** VECTOR / WEB_SEARCH 写入后端的 integration_backend 码（下拉项仍分 vectorBackends / webSearchProviders）。 */
export type LlmIntegrationBackendCode = LlmVectorBackendCode | LlmWebSearchProviderCode;

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
  /** 后端写入：MessageSource 使用的语言标签；无此字段说明响应非当前 Ai meta 实现或网关改写了 body */
  metaResolvedLocale?: string | null;
  /** 后端写入：收到的 lang 查询参数 */
  metaLangParamRaw?: string | null;
}

export interface LlmModelAdminView {
  id: number;
  alias: string;
  displayName: string;
  openaiBaseUrl: string;
  openaiModelId: string;
  modelKind: LlmModelKindCode;
  /** {@code llm_model.integration_backend}：VECTOR 为嵌入策略码；WEB_SEARCH 为联网实现码 */
  integrationBackend: string;
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
  /** 仅 LANGUAGE：主备链备用模型别名 */
  fallbackModelAlias?: string | null;
}

export interface CreateLlmModelBody {
  alias: string;
  displayName: string;
  openaiBaseUrl: string;
  openaiModelId: string;
  /** 省略时后端默认 `LANGUAGE` */
  modelKind?: LlmModelKindCode;
  /** VECTOR / WEB_SEARCH：写入 integration_backend */
  integrationBackend?: LlmIntegrationBackendCode | string;
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
  /** 仅 LANGUAGE：主备备用别名；可空 */
  fallbackModelAlias?: string | null;
}

/** 须传入当前界面语言；协商参数见 {@link adminUiNegotiationHeaders}。 */
export async function getLlmModelMeta(acceptLanguage: AdminUiLocaleTag): Promise<LlmModelAdminMetaResponse> {
  const { data } = await http.get<LlmModelAdminMetaResponse>("/api/v1/admin/llm-models/meta", {
    headers: adminUiNegotiationHeaders(acceptLanguage),
    params: adminUiNegotiationParams(acceptLanguage),
  });
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
    integrationBackend?: LlmIntegrationBackendCode | string;
    apiKey?: string;
    /** true：清除已保存的 API Key（向量模型免鉴权等） */
    clearApiKey?: boolean;
    /** true：改为不限制额度 */
    tokenQuotaUnlimited?: boolean;
    tokenQuotaTotal?: number | null;
    localDeploy?: boolean;
    fallbackModelAlias?: string | null;
  },
): Promise<LlmModelAdminView> {
  const { data } = await http.put<LlmModelAdminView>(`/api/v1/admin/llm-models/${id}`, body);
  return data;
}

export async function deleteLlmModel(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/llm-models/${id}`);
}
