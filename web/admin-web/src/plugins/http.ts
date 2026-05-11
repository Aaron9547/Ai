import axios from "axios";
import { readJwtTid } from "@/utils/jwtSubject";

export const AI_ADMIN_ACCESS_TOKEN_KEY = "ai_admin_access_token";

/**
 * 历史键：工作区切换已改为重新签发 JWT，**X-Tenant-Id 以 JWT {@code tid} 为唯一真源**。
 * 登录/切换成功后会清除，避免与令牌漂移。
 */
export const AI_ADMIN_EFFECTIVE_TENANT_KEY = "ai_admin_effective_tenant_id";

/** 登录响应中的 memberships JSON 缓存（管理端工作与角色切换等沿用）。 */
export const AI_ADMIN_MEMBERSHIPS_KEY = "ai_admin_memberships_json";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "",
  timeout: 60000,
});

function resolveRequestTenantId(token: string | null): string {
  const fallback = import.meta.env.VITE_TENANT_ID || "1";
  const tid = readJwtTid(token);
  if (tid && /^\d+$/.test(tid.trim())) {
    return tid.trim();
  }
  return fallback;
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY);
  const tenantId = resolveRequestTenantId(token);
  config.headers["X-Tenant-Id"] = tenantId;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (res) => res,
  async (err: unknown) => {
    if (axios.isAxiosError(err) && err.response?.status === 401) {
      localStorage.removeItem(AI_ADMIN_ACCESS_TOKEN_KEY);
      if (import.meta.env.VITE_ADMIN_AUTH_SKIP !== "true") {
        const { default: r } = await import("@/router");
        if (r.currentRoute.value.path !== "/login") {
          void r.replace({
            path: "/login",
            query: { redirect: r.currentRoute.value.fullPath },
          });
        }
      }
    }
    return Promise.reject(err);
  },
);
