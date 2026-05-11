/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string;
  /** 与 `sys_tenant.code` 一致；用于 `/`、`/chat` 重定向默认租户 */
  readonly VITE_TENANT_CODE: string;
  readonly VITE_TENANT_ID: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
