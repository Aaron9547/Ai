import { http } from "@/plugins/http";

export type CorsOriginRow = {
  id: number;
  origin: string;
  enabled: "ON" | "OFF";
  sortOrder: number;
  remark: string | null;
};

export async function listCorsOrigins(): Promise<CorsOriginRow[]> {
  const { data } = await http.get<CorsOriginRow[]>("/api/v1/admin/cors-allowed-origins");
  return data;
}

export async function createCorsOrigin(body: {
  origin: string;
  enabled: "ON" | "OFF";
  sortOrder: number;
  remark?: string;
}): Promise<CorsOriginRow> {
  const { data } = await http.post<CorsOriginRow>("/api/v1/admin/cors-allowed-origins", body);
  return data;
}

export async function updateCorsOrigin(
  id: number,
  body: { origin: string; enabled: "ON" | "OFF"; sortOrder: number; remark?: string | null },
): Promise<CorsOriginRow> {
  const { data } = await http.put<CorsOriginRow>(`/api/v1/admin/cors-allowed-origins/${id}`, body);
  return data;
}

export async function deleteCorsOrigin(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/cors-allowed-origins/${id}`);
}
