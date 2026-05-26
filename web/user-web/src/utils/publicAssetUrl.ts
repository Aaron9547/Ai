/** 将 Shell 配置的相对路径（如 /open/v1/admin-brand-logos/...）解析为可加载的绝对 URL。 */
export function resolvePublicAssetUrl(raw: string | undefined | null): string {
  const u = (raw ?? "").trim();
  if (!u) return "";
  if (/^https?:\/\//i.test(u)) return u;
  const base = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");
  const path = u.startsWith("/") ? u : `/${u}`;
  return base ? `${base}${path}` : path;
}
