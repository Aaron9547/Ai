import { http } from "../plugins/http";
import {
  normalizeAdminUserProfileDetail,
  type AdminUserProfileDetail,
} from "../utils/userProfileDetailSemantics";

export type { AdminUserProfileDetail, AdminRecentMemoryChunk } from "../utils/userProfileDetailSemantics";

export interface UserProfileSummaryRow {
  userId: number;
  loginName: string;
  displayName: string;
  profileTagCount: number;
  memoryAbstractPresent: boolean;
  memoryChunkCount: number;
}

export interface UserProfilePageResult {
  records: UserProfileSummaryRow[];
  total: number;
  page: number;
  size: number;
}

export interface VectorModelOption {
  id: number;
  alias: string;
  displayName: string;
  active: boolean;
}

export interface MemoryEmbeddingModelSettingView {
  selectedLlmModelId: number | null;
  vectorModels: VectorModelOption[];
}

export async function listUserProfiles(params: {
  page?: number;
  size?: number;
  keyword?: string;
}): Promise<UserProfilePageResult> {
  const { data } = await http.get<UserProfilePageResult>("/api/v1/admin/user-profiles", { params });
  return data;
}

export async function getUserProfileDetail(userId: number): Promise<AdminUserProfileDetail> {
  const { detail } = await fetchUserProfileDetail(userId);
  return detail;
}

/** 拉取详情并同时返回原始 JSON（用于管理端「原始 JSON」排障）。 */
export async function fetchUserProfileDetail(
  userId: number,
): Promise<{ raw: unknown; detail: AdminUserProfileDetail }> {
  const { data } = await http.get<unknown>(`/api/v1/admin/user-profiles/${userId}`);
  return { raw: data, detail: normalizeAdminUserProfileDetail(data) };
}

export async function getMemoryEmbeddingModel(): Promise<MemoryEmbeddingModelSettingView> {
  const { data } = await http.get<MemoryEmbeddingModelSettingView>(
    "/api/v1/admin/user-profiles/memory-embedding-model",
  );
  return data;
}

export async function putMemoryEmbeddingModel(body: { llmModelId: number | null }): Promise<void> {
  await http.put("/api/v1/admin/user-profiles/memory-embedding-model", body);
}
