/** Vite dev / preview 穿透域名白名单（user-web、admin-web 共用）。 */
const BASE_ALLOWED_HOSTS = [".vicp.fun", ".vicp.cc", ".onecraft.ca"];

/**
 * 合并仓库默认后缀与本地追加域名。
 * 环境变量：`VITE_ADDITIONAL_ALLOWED_HOSTS` 或 Vite 文档中的 `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`（逗号分隔）；
 * `VITE_ALLOWED_HOSTS_ALL=true` 时等价于 `allowedHosts: true`（仅本地调试，勿提交）。
 */
export function resolveViteAllowedHosts(): string[] | true {
  if (process.env.VITE_ALLOWED_HOSTS_ALL === "true") {
    return true;
  }
  const extra = (
    process.env.VITE_ADDITIONAL_ALLOWED_HOSTS ??
    process.env.__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS ??
    ""
  )
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  return [...BASE_ALLOWED_HOSTS, ...extra];
}
