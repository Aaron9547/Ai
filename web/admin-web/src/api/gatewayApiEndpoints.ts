import { http } from "../plugins/http";

export type ApiEndpointRow = {
  id: number;
  pathPattern: string;
  httpMethod: string;
  displayName: string;
  remark?: string | null;
  enabled: "ON" | "OFF";
  sortOrder?: number | null;
  requestSpecJson?: string | null;
  responseSpecJson?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type ApiEndpointPage = {
  records: ApiEndpointRow[];
  total: number;
  size: number;
  current: number;
};

export async function listApiEndpoints(page = 1, size = 20): Promise<ApiEndpointPage> {
  const { data } = await http.get<ApiEndpointPage>("/api/v1/admin/gateway-api-endpoints", { params: { page, size } });
  return data;
}

export async function listApiEndpointPicker(): Promise<ApiEndpointRow[]> {
  const { data } = await http.get<ApiEndpointRow[]>("/api/v1/admin/gateway-api-endpoints/picker");
  return data;
}

export async function createApiEndpoint(body: {
  pathPattern: string;
  httpMethod?: string;
  displayName: string;
  remark?: string;
  enabled?: "ON" | "OFF";
  sortOrder?: number;
  requestSpecJson?: string;
  responseSpecJson?: string;
}): Promise<ApiEndpointRow> {
  const { data } = await http.post<ApiEndpointRow>("/api/v1/admin/gateway-api-endpoints", body);
  return data;
}

export async function updateApiEndpoint(
  id: number,
  body: {
    pathPattern?: string;
    httpMethod?: string;
    displayName?: string;
    remark?: string;
    enabled?: "ON" | "OFF";
    sortOrder?: number;
    requestSpecJson?: string;
    responseSpecJson?: string;
  },
): Promise<ApiEndpointRow> {
  const { data } = await http.put<ApiEndpointRow>(`/api/v1/admin/gateway-api-endpoints/${id}`, body);
  return data;
}

export type OpenApiSpecSyncResult = {
  updated: number;
  skipped: number;
  unmatched: number;
  unmatchedSamples: string[];
};

export async function syncOpenApiSpec(emptyOnly = true): Promise<OpenApiSpecSyncResult> {
  const { data } = await http.post<OpenApiSpecSyncResult>(
    "/api/v1/admin/gateway-api-endpoints/sync-openapi-spec",
    null,
    { params: { emptyOnly } },
  );
  return data;
}

export async function syncOpenApiSpecOne(id: number, emptyOnly = true): Promise<ApiEndpointRow> {
  const { data } = await http.post<ApiEndpointRow>(
    `/api/v1/admin/gateway-api-endpoints/${id}/sync-openapi-spec`,
    null,
    { params: { emptyOnly } },
  );
  return data;
}

export async function deleteApiEndpoint(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/gateway-api-endpoints/${id}`);
}
