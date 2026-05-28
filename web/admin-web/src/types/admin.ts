import type { MybatisPage } from "./page";

/** 与后端 `SysHttpAccessLog` JSON 对齐 */
export interface AccessLogRow {
  id: number;
  tenantId: number;
  tenantCode?: string | null;
  tenantName?: string | null;
  userDisplayName?: string | null;
  userId?: number | null;
  deviceId?: string | null;
  method: string;
  pathPattern: string;
  httpStatus: number;
  durationMs: number;
  traceId?: string | null;
  userAgent?: string | null;
  clientIp?: string | null;
  createdAt?: string | null;
}

export type AccessLogPage = MybatisPage<AccessLogRow>;

/** 与后端 `SysAuditEvent` JSON 对齐 */
export interface AuditEventRow {
  id: number;
  tenantId: number;
  /** 管理端展示用，与 `sys_tenant.code` 一致 */
  tenantCode?: string | null;
  tenantName?: string | null;
  actorType?: string | null;
  actorDisplayName?: string | null;
  actorId?: string | null;
  action?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  /** 关联对象可读摘要（如成员 · 登录名）；与后端 `SysAuditEvent.resourceDisplaySummary` 对齐 */
  resourceDisplaySummary?: string | null;
  detailJson?: string | null;
  createdAt?: string | null;
}

export type AuditEventPage = MybatisPage<AuditEventRow>;

/** 与后端 `MeteringUsageEvent` JSON 对齐 */
export interface MeteringEventRow {
  id: number;
  tenantId: number;
  tenantCode?: string | null;
  tenantName?: string | null;
  userDisplayName?: string | null;
  userId?: number | null;
  deviceId?: string | null;
  meterType?: string | null;
  quantity?: number | string | null;
  unit?: string | null;
  refJson?: string | null;
  createdAt?: string | null;
}

export type MeteringEventPage = MybatisPage<MeteringEventRow>;

/** 与后端 `MeteringUsageBySceneView` 对齐 */
export interface MeteringUsageBySceneRow {
  usageScene: string;
  totalTokens: number;
  eventCount: number;
}

/** 与后端 `McpServerRegistry` 对齐 */
export interface McpServerRow {
  id: number;
  tenantId: number;
  name: string;
  baseUrl: string;
  status?: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** 与后端 `RagKbAdminView` 对齐 */
export interface RagKnowledgeBaseRow {
  id: number;
  tenantId: number;
  name: string;
  defaultChunkStrategy?: string;
  chunkFixedChars?: number;
  chunkSlideOverlap?: number;
  assignedLlmModelId?: number | null;
  /** 绑定的嵌入模型 llm_model.id（VECTOR） */
  assignedEmbeddingModelId?: number | null;
  /** ToggleState 名：ON / OFF */
  chatRetrievalEnabled?: string;
  /** 本库对话 Milvus COSINE 下限；0 表示关闭该库向量分数过滤 */
  chatVectorMinCosineScore?: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RagDocumentAdminRow {
  id: number;
  tenantId: number;
  title: string;
  sourceType: string;
  sourceUri?: string | null;
  originalFilename?: string | null;
  contentLength: number;
  categoryId?: number | null;
  categoryName?: string | null;
  displayStatus?: string | null;
  applicableScope?: string | null;
  uploadedByUserId?: number | null;
  uploadedByLabel?: string | null;
  /** 对话 RAG 召回写入助手 meta 后累计的文档命中次数 */
  hitCount?: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** 知识库文档分类（管理端） */
export interface RagDocumentCategoryAdminRow {
  id: number;
  kbId: number;
  name: string;
  sortOrder: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** 与 MyBatis-Plus `Page` JSON 对齐 */
export interface MybatisPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages?: number;
}

export interface RagChunkAdminRow {
  id: number;
  seq: number;
  content: string;
  embeddingRef?: string | null;
  /** ENABLED | DISABLED */
  retrievalEnabled?: string | null;
  contentLength?: number;
  /** 对话 RAG 召回累计命中该分片的次数 */
  hitCount?: number;
  parentChunkId?: number | null;
  /** FLAT | PARENT | CHILD */
  chunkRole?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** 与后端 `JobTaskAdminDtos.JobTaskAdminView` 对齐 */
export interface JobTaskAdminRow {
  id: number;
  taskType: string;
  status: string;
  payloadJson?: string | null;
  resultJson?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}
