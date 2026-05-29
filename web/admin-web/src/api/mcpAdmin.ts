import { http } from "../plugins/http";
import type { McpServerRow } from "../types/admin";
import type { MybatisPage } from "../types/page";

export async function fetchMcpServersPage(page = 1, size = 20): Promise<MybatisPage<McpServerRow>> {
  const { data } = await http.get<MybatisPage<McpServerRow>>("/api/v1/admin/mcp-servers", {
    params: { page, size },
  });
  return data;
}

export interface CreateMcpServerBody {
  name: string;
  baseUrl: string;
  transportKind?: string;
  description?: string;
  apiKey?: string;
  enabled?: boolean;
}

export interface UpdateMcpServerBody {
  name?: string;
  baseUrl?: string;
  transportKind?: string;
  description?: string;
  apiKey?: string;
  enabled?: boolean;
}

export interface McpProbeResult {
  ok: boolean;
  message: string;
  toolCount: number;
  elapsedMs: number;
}

export async function createMcpServer(body: CreateMcpServerBody): Promise<McpServerRow> {
  const { data } = await http.post<McpServerRow>("/api/v1/admin/mcp-servers", body);
  return data;
}

export async function updateMcpServer(id: number, body: UpdateMcpServerBody): Promise<McpServerRow> {
  const { data } = await http.put<McpServerRow>(`/api/v1/admin/mcp-servers/${id}`, body);
  return data;
}

export async function deleteMcpServer(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/mcp-servers/${id}`);
}

export async function probeMcpServer(id: number): Promise<McpProbeResult> {
  const { data } = await http.post<McpProbeResult>(`/api/v1/admin/mcp-servers/${id}/probe`);
  return data;
}
