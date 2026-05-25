import { http } from "../plugins/http";

export type AccessPartyRow = {
  id: number;
  tenantId: number;
  appId: string;
  displayName: string;
  status: "ON" | "OFF";
  totalRpmCap: number;
  remark?: string | null;
  lastRotatedAt?: string | null;
};

export type AccessPartyPage = {
  records: AccessPartyRow[];
  total: number;
  size: number;
  current: number;
};

export type ApiModuleRow = {
  id: number;
  code: string;
  displayName: string;
  sortOrder: number;
  enabled: "ON" | "OFF";
  remark?: string | null;
};

export type GrantView = {
  grant: {
    id: number;
    accessPartyId: number;
    endpointId: number;
    moduleId?: number | null;
    grantedRpm: number;
    enabled: "ON" | "OFF";
  };
  endpoint?: {
    id: number;
    pathPattern: string;
    httpMethod: string;
    displayName: string;
    globalRpmCap?: number;
  } | null;
  party?: AccessPartyRow | null;
};

export type ModuleEndpointLink = {
  id: number;
  moduleId: number;
  endpointId: number;
  createdAt?: string;
};

export type AuditLogRow = {
  id: number;
  tenantId: number;
  accessPartyId: number;
  endpointId?: number | null;
  method: string;
  pathPattern: string;
  httpStatus: number;
  durationMs: number;
  clientIp?: string | null;
  tokensConsumed: number;
  errorCode?: string | null;
  createdAt: string;
};

export async function listAccessParties(page = 1, size = 20): Promise<AccessPartyPage> {
  const { data } = await http.get<AccessPartyPage>("/api/v1/admin/gateway-access-parties", {
    params: { page, size },
  });
  return data;
}

export async function createAccessParty(body: {
  displayName: string;
  appId?: string;
  totalRpmCap?: number;
  remark?: string;
  status?: "ON" | "OFF";
}): Promise<{ party: AccessPartyRow; plainSecret: string }> {
  const { data } = await http.post<{ party: AccessPartyRow; plainSecret: string }>(
    "/api/v1/admin/gateway-access-parties",
    body,
  );
  return data;
}

export async function updateAccessParty(
  id: number,
  body: { displayName?: string; totalRpmCap?: number; remark?: string; status?: "ON" | "OFF" },
): Promise<AccessPartyRow> {
  const { data } = await http.put<AccessPartyRow>(`/api/v1/admin/gateway-access-parties/${id}`, body);
  return data;
}

export async function rotateAccessPartySecret(id: number): Promise<string> {
  const { data } = await http.post<{ plainSecret: string }>(
    `/api/v1/admin/gateway-access-parties/${id}/rotate-secret`,
  );
  return data.plainSecret;
}

export async function deleteAccessParty(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/gateway-access-parties/${id}`);
}

export async function listApiModules(): Promise<ApiModuleRow[]> {
  const { data } = await http.get<ApiModuleRow[]>("/api/v1/admin/gateway-api-modules");
  return data;
}

export async function createApiModule(body: {
  code: string;
  displayName: string;
  sortOrder?: number;
  enabled?: "ON" | "OFF";
  remark?: string;
}): Promise<ApiModuleRow> {
  const { data } = await http.post<ApiModuleRow>("/api/v1/admin/gateway-api-modules", body);
  return data;
}

export async function updateApiModule(
  id: number,
  body: { displayName?: string; sortOrder?: number; enabled?: "ON" | "OFF"; remark?: string },
): Promise<ApiModuleRow> {
  const { data } = await http.put<ApiModuleRow>(`/api/v1/admin/gateway-api-modules/${id}`, body);
  return data;
}

export async function deleteApiModule(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/gateway-api-modules/${id}`);
}

export async function listModuleEndpoints(moduleId: number): Promise<ModuleEndpointLink[]> {
  const { data } = await http.get<ModuleEndpointLink[]>(`/api/v1/admin/gateway-api-modules/${moduleId}/endpoints`);
  return data;
}

export async function replaceModuleEndpoints(moduleId: number, endpointIds: number[]): Promise<void> {
  await http.put(`/api/v1/admin/gateway-api-modules/${moduleId}/endpoints`, { endpointIds });
}

export async function updateGrantRpm(
  accessPartyId: number,
  grantId: number,
  grantedRpm: number,
): Promise<void> {
  await http.post(`/api/v1/admin/gateway-access-party-grants/${grantId}/rpm`, { grantedRpm }, {
    params: { accessPartyId },
  });
}

export async function fetchGrantWizard(partyId: number): Promise<GrantWizardView> {
  const { data } = await http.get<GrantWizardView>(`/api/v1/admin/gateway-access-parties/${partyId}/grant-wizard`);
  return data;
}

export type GrantWizardModuleStep = {
  module: ApiModuleRow;
  endpoints: import("./gatewayApiEndpoints").ApiEndpointRow[];
};

export type GrantWizardView = {
  party: AccessPartyRow;
  modules: GrantWizardModuleStep[];
  grants: GrantView[];
};

export async function fetchIntegrationDoc(
  id: number,
  baseUrl?: string,
): Promise<{ filename: string; markdown: string }> {
  const params: Record<string, string> = {};
  if (baseUrl?.trim()) params.baseUrl = baseUrl.trim();
  const { data } = await http.get<{ filename: string; markdown: string }>(
    `/api/v1/admin/gateway-access-parties/${id}/integration-doc`,
    { params },
  );
  return data;
}

export function downloadMarkdownFile(filename: string, markdown: string) {
  const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(".md") ? filename : `${filename}.md`;
  a.click();
  URL.revokeObjectURL(url);
}

export async function listGrants(accessPartyId: number): Promise<GrantView[]> {
  const { data } = await http.get<GrantView[]>("/api/v1/admin/gateway-access-party-grants", {
    params: { accessPartyId },
  });
  return data;
}

export async function replaceGrants(
  accessPartyId: number,
  items: { endpointId: number; moduleId?: number; grantedRpm: number; enabled?: "ON" | "OFF" }[],
): Promise<void> {
  await http.put("/api/v1/admin/gateway-access-party-grants", { items }, { params: { accessPartyId } });
}

export async function auditSummary(days = 7): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>("/api/v1/admin/gateway-access-party-audit/summary", {
    params: { days },
  });
  return data;
}

export async function listAuditLogs(
  page = 1,
  size = 20,
  accessPartyId?: number,
): Promise<{ records: AuditLogRow[]; total: number }> {
  const params: Record<string, string | number> = { page, size };
  if (accessPartyId != null) params.accessPartyId = accessPartyId;
  const { data } = await http.get<{ records: AuditLogRow[]; total: number }>(
    "/api/v1/admin/gateway-access-party-audit",
    { params },
  );
  return data;
}
