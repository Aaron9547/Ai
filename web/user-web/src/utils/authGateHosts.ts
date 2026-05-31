/** 公网穿透域名：未登录时跳转 `/:tenantCode/auth`（可通过 `VITE_AUTH_GATE_HOSTS` 覆盖，设 `false` 关闭）。 */
export function resolveAuthGateHostPatterns(): string[] {
  const raw = import.meta.env.VITE_AUTH_GATE_HOSTS as string | undefined;
  if (raw === "false" || raw === "0") {
    return [];
  }
  if (raw?.trim()) {
    return raw.split(",").map((s) => s.trim()).filter(Boolean);
  }
  return [".onecraft.ca"];
}

export function hostMatchesPattern(hostname: string, pattern: string): boolean {
  const h = hostname.toLowerCase();
  const p = pattern.toLowerCase();
  if (p.startsWith(".")) {
    const base = p.slice(1);
    return h === base || h.endsWith(p);
  }
  return h === p;
}

export function isAuthGatedHost(hostname?: string): boolean {
  const h = (hostname ?? (typeof window !== "undefined" ? window.location.hostname : "")).trim();
  if (!h || h === "localhost" || h === "127.0.0.1") {
    return false;
  }
  const patterns = resolveAuthGateHostPatterns();
  return patterns.some((pattern) => hostMatchesPattern(h, pattern));
}
