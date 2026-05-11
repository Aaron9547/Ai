import { http } from "../plugins/http";
import type { AccessLogPage, AuditEventPage, MeteringEventPage } from "../types/admin";

export async function fetchAccessLogs(
  page = 1,
  size = 20,
  opts?: { filterTenantId?: number },
): Promise<AccessLogPage> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) {
    params.filterTenantId = opts.filterTenantId;
  }
  const { data } = await http.get<AccessLogPage>("/api/v1/admin/access-logs", { params });
  return data;
}

export async function fetchAudit(
  page = 1,
  size = 20,
  opts?: { filterTenantId?: number },
): Promise<AuditEventPage> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) {
    params.filterTenantId = opts.filterTenantId;
  }
  const { data } = await http.get<AuditEventPage>("/api/v1/admin/audit-events", { params });
  return data;
}

export async function fetchMetering(
  page = 1,
  size = 20,
  opts?: { filterTenantId?: number },
): Promise<MeteringEventPage> {
  const params: Record<string, unknown> = { page, size };
  if (opts?.filterTenantId != null) {
    params.filterTenantId = opts.filterTenantId;
  }
  const { data } = await http.get<MeteringEventPage>("/api/v1/admin/metering-events", { params });
  return data;
}
