/**
 * 公网 Host 登录门禁域名模式（默认关闭，与本机 IP / localhost 一致允许访客进聊天）。
 * 部署需强制登录时设置 `VITE_AUTH_GATE_HOSTS=.onecraft.ca`（逗号分隔多个）；设 `false` 显式关闭。
 */
export function resolveAuthGateHostPatterns(): string[] {
  const raw = import.meta.env.VITE_AUTH_GATE_HOSTS as string | undefined;
  if (raw === "false" || raw === "0") {
    return [];
  }
  if (raw?.trim()) {
    return raw.split(",").map((s) => s.trim()).filter(Boolean);
  }
  return [];
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
