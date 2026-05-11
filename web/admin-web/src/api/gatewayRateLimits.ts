import { http } from "../plugins/http";

export type RateLimitRow = {
  id: number;
  tenantId?: number | null;
  pathPattern: string;
  httpMethod: string;
  requestsPerMinute: number;
  enabled: "ON" | "OFF";
  remark?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type RateLimitPage = {
  records: RateLimitRow[];
  total: number;
  size: number;
  current: number;
};

export type RateLimitListParams = {
  page?: number;
  size?: number;
  /** 创始人：GLOBAL 仅全局限流；否则与 tenantId 组合为租户级 */
  rateScope?: "GLOBAL" | "TENANT";
  tenantId?: number;
};

export async function listRateLimits(page = 1, size = 20, extra?: RateLimitListParams): Promise<RateLimitPage> {
  const params: Record<string, string | number> = { page, size };
  if (extra?.rateScope) params.rateScope = extra.rateScope;
  if (extra?.tenantId != null) params.tenantId = extra.tenantId;
  const { data } = await http.get<RateLimitPage>("/api/v1/admin/gateway-rate-limits", { params });
  return data;
}

export async function createRateLimit(body: {
  tenantId?: number | null;
  pathPattern: string;
  httpMethod?: string;
  requestsPerMinute?: number;
  enabled?: "ON" | "OFF";
  remark?: string;
}): Promise<RateLimitRow> {
  const { data } = await http.post<RateLimitRow>("/api/v1/admin/gateway-rate-limits", body);
  return data;
}

export async function updateRateLimit(
  id: number,
  body: {
    tenantId?: number | null;
    pathPattern?: string;
    httpMethod?: string;
    requestsPerMinute?: number;
    enabled?: "ON" | "OFF";
    remark?: string;
  },
): Promise<RateLimitRow> {
  const { data } = await http.put<RateLimitRow>(`/api/v1/admin/gateway-rate-limits/${id}`, body);
  return data;
}

export async function deleteRateLimit(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/gateway-rate-limits/${id}`);
}
