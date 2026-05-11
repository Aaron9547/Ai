import { ref } from "vue";

/**
 * 登录后写入的「当前工作区」：应为 **`sys_tenant.code`**（与路径第一段一致）；历史版本可能仅存数字 id。
 * 与 {@link ../plugins/http} 重导出同名。
 */
export const AI_USER_EFFECTIVE_TENANT_KEY = "ai_user_effective_tenant_id";

/** 与库表 `sys_tenant.code`（VARCHAR(64)）对齐：字母数字与 `._-` */
export const TENANT_CODE_PATH_RE = /^[a-zA-Z0-9._-]{1,64}$/;

/**
 * 当前地址为 `/:tenantCode/...` 时由路由守卫写入；出站时优先发 **`X-Tenant-Code`**（勿与数字 **`X-Tenant-Id`** 同时误传覆盖）。
 */
export const routeBoundTenantCode = ref("");

export function setRouteBoundTenantCode(code: string | undefined | null): void {
  const s = code?.trim() ?? "";
  routeBoundTenantCode.value = TENANT_CODE_PATH_RE.test(s) ? s : "";
}

export type OutboundTenantHeaders = {
  "X-Tenant-Id"?: string;
  "X-Tenant-Code"?: string;
};

/**
 * 构建出站租户头，与 {@link com.aaron.cloud.gateway.TenantContextFilter#resolveTenantIdFromHeaders} 一致：
 * 有 **`X-Tenant-Id`** 则按数字解析；否则 **`X-Tenant-Code`** 查编码。
 */
export function buildOutboundTenantHeaders(): OutboundTenantHeaders {
  const fromRoute = routeBoundTenantCode.value.trim();
  if (fromRoute) {
    return { "X-Tenant-Code": fromRoute };
  }
  try {
    const v = localStorage.getItem(AI_USER_EFFECTIVE_TENANT_KEY)?.trim();
    if (v) {
      if (/^\d+$/.test(v)) {
        return { "X-Tenant-Id": v };
      }
      if (TENANT_CODE_PATH_RE.test(v)) {
        return { "X-Tenant-Code": v };
      }
    }
  } catch {
    /* ignore */
  }
  const defCode = String(import.meta.env.VITE_TENANT_CODE || "default").trim();
  if (defCode && TENANT_CODE_PATH_RE.test(defCode)) {
    return { "X-Tenant-Code": defCode };
  }
  const defId = String(import.meta.env.VITE_TENANT_ID || "1").trim();
  return { "X-Tenant-Id": defId || "1" };
}
