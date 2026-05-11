import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import {
  AI_USER_EFFECTIVE_TENANT_KEY,
  buildOutboundTenantHeaders,
} from "../utils/outboundTenant";
import { clearUserMemberships } from "../utils/userMembershipStorage";

const DEVICE_KEY = "ai_device_id";

/** 标记：已对 /open/ 上 401 清会话并重试过，避免死循环 */
const REOPEN_401_RETRIED = "__reopen401Retried";

/**
 * 非生产构建：页签用局域网 IP/主机名打开时，若仍使用指向本机 loopback 的 VITE_API_BASE，
 * 请求会落到「浏览器所在机器」的 127.0.0.1，而非跑 Vite/后端的开发机。此时强制走同源 + Vite 代理。
 */
export function resolveApiBaseForBrowser(): string {
  const raw = (import.meta.env.VITE_API_BASE as string | undefined)?.trim() ?? "";
  if (!raw) return "";
  if (import.meta.env.PROD || typeof window === "undefined") return raw;
  try {
    const apiHost = new URL(raw).hostname;
    const pageHost = window.location.hostname;
    const apiIsLoopback = apiHost === "localhost" || apiHost === "127.0.0.1";
    const pageIsNotLoopback =
      pageHost !== "" && pageHost !== "localhost" && pageHost !== "127.0.0.1";
    if (apiIsLoopback && pageIsNotLoopback) {
      return "";
    }
  } catch {
    return raw;
  }
  return raw;
}

/** C 端用户 JWT（与设备码并存；访客可不设置） */
export const AI_USER_ACCESS_TOKEN_KEY = "ai_user_access_token";

export { AI_USER_EFFECTIVE_TENANT_KEY };

/** 部分旧浏览器 / 内嵌 WebView 仅有 {@code crypto.getRandomValues} 而无 {@code randomUUID}。 */
function createRandomUuidV4(): string {
  const c = globalThis.crypto;
  if (typeof c?.randomUUID === "function") {
    return c.randomUUID();
  }
  if (typeof c?.getRandomValues === "function") {
    const b = new Uint8Array(16);
    c.getRandomValues(b);
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }
  return `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`.replace(/[xy]/g, (ch) => {
    const n = (Math.random() * 16) | 0;
    const v = ch === "x" ? n : (n & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** 与 axios 拦截器、SSE fetch 共用，保证访客设备码一致上送。 */
export function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = createRandomUuidV4();
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

export function getUserAccessToken(): string | null {
  return localStorage.getItem(AI_USER_ACCESS_TOKEN_KEY);
}

export function clearUserSession(): void {
  localStorage.removeItem(AI_USER_ACCESS_TOKEN_KEY);
  localStorage.removeItem(AI_USER_EFFECTIVE_TENANT_KEY);
  clearUserMemberships();
}

export const http = axios.create({
  baseURL: resolveApiBaseForBrowser(),
  timeout: 120000,
});

function applyOutboundTenantHeaders(headers: InternalAxiosRequestConfig["headers"]): void {
  if (!headers) return;
  const h = buildOutboundTenantHeaders();
  if (headers instanceof AxiosHeaders) {
    headers.delete("X-Tenant-Id");
    headers.delete("X-Tenant-Code");
    if (h["X-Tenant-Id"]) headers.set("X-Tenant-Id", h["X-Tenant-Id"]);
    if (h["X-Tenant-Code"]) headers.set("X-Tenant-Code", h["X-Tenant-Code"]);
  } else if (typeof headers === "object") {
    const o = headers as Record<string, unknown>;
    delete o["X-Tenant-Id"];
    delete o["X-Tenant-Code"];
    if (h["X-Tenant-Id"]) (headers as Record<string, string>)["X-Tenant-Id"] = h["X-Tenant-Id"];
    if (h["X-Tenant-Code"]) (headers as Record<string, string>)["X-Tenant-Code"] = h["X-Tenant-Code"];
  }
}

http.interceptors.request.use((config) => {
  config.baseURL = resolveApiBaseForBrowser();
  applyOutboundTenantHeaders(config.headers);
  config.headers["X-Device-Id"] = getOrCreateDeviceId();
  const token = getUserAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Spring OAuth2 资源服务器在请求携带非法 Bearer 时仍会对 {@code /open/**} 返回 401；{@code permitAll} 不跳过 JWT 校验。
 * {@code localhost:5173} 与 {@code 本机IP:5173} 为不同源、localStorage 独立，IP 站点易残留坏令牌导致「加载模型列表失败」。
 */
http.interceptors.response.use(
  (r) => r,
  async (err: unknown) => {
    if (!axios.isAxiosError(err) || err.response?.status !== 401 || !err.config) {
      return Promise.reject(err);
    }
    const cfg = err.config as InternalAxiosRequestConfig & { [REOPEN_401_RETRIED]?: boolean };
    const path = `${cfg.baseURL ?? ""}${cfg.url ?? ""}`;
    if (!path.includes("/open/") || cfg[REOPEN_401_RETRIED]) {
      return Promise.reject(err);
    }
    clearUserSession();
    cfg[REOPEN_401_RETRIED] = true;
    const h = cfg.headers;
    if (h instanceof AxiosHeaders) {
      h.delete("Authorization");
    } else if (h && typeof h === "object") {
      delete (h as Record<string, unknown>).Authorization;
    }
    return http.request(cfg);
  },
);
