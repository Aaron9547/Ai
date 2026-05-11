import { http } from "../plugins/http";

export type MenuItemRow = {
  id: number;
  menuCode: string;
  titleZh: string;
  routePath?: string | null;
  sortOrder: number;
  enabled: "ON" | "OFF";
  createdAt?: string;
  updatedAt?: string;
};

export async function listMenuItems(): Promise<MenuItemRow[]> {
  const { data } = await http.get<MenuItemRow[]>("/api/v1/admin/menu-items");
  return data;
}

export async function createMenuItem(body: {
  menuCode: string;
  titleZh?: string;
  routePath?: string;
  sortOrder?: number;
}): Promise<MenuItemRow> {
  const { data } = await http.post<MenuItemRow>("/api/v1/admin/menu-items", body);
  return data;
}

export async function updateMenuItem(
  id: number,
  body: { titleZh?: string; routePath?: string; sortOrder?: number; enabled?: "ON" | "OFF" },
): Promise<MenuItemRow> {
  const { data } = await http.put<MenuItemRow>(`/api/v1/admin/menu-items/${id}`, body);
  return data;
}

export async function deleteMenuItem(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/menu-items/${id}`);
}
