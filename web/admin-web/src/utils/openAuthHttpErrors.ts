import axios from "axios";

/** Spring Web / Security 在 Origin 不在白名单时常见响应体（纯文本）。 */
const BACKEND_CORS_BLOCKED_MESSAGE =
  "当前页面地址或端口与后端允许的开发来源不一致，请求被浏览器拦截，并非账号或密码错误。请改用文档约定的本地地址与端口，或由管理员在后端扩展允许的访问来源。";

const BACKEND_UNREACHABLE_MESSAGE =
  "无法连上登录服务：请确认后端已启动、网络正常；若本页地址或端口与后端配置不一致，也会出现此提示（不一定是账号或密码错误）。";

function responseBodyText(data: unknown): string {
  if (data == null) {
    return "";
  }
  if (typeof data === "string") {
    return data;
  }
  if (typeof data === "object" && data !== null && "message" in data) {
    const m = (data as { message?: unknown }).message;
    if (typeof m === "string") {
      return m;
    }
  }
  return "";
}

export function isBackendCorsBlocked(err: unknown): boolean {
  if (!axios.isAxiosError(err)) {
    return false;
  }
  const status = err.response?.status;
  if (status !== 403) {
    return false;
  }
  const t = responseBodyText(err.response?.data).toLowerCase();
  return t.includes("invalid cors") || t.includes("cors request");
}

export function backendCorsBlockedMessage(): string {
  return BACKEND_CORS_BLOCKED_MESSAGE;
}

/** 开放认证登录（POST /open/v1/auth/login）失败时的用户可见说明。 */
export function openAuthLoginErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err)) {
    return "登录未成功，请稍后重试。";
  }
  if (isBackendCorsBlocked(err)) {
    return BACKEND_CORS_BLOCKED_MESSAGE;
  }

  const status = err.response?.status;
  if (status === 401) {
    return "账号或密码不正确，请修改后重试。";
  }
  if (status === 403) {
    const code = (err.response?.data as { code?: string } | undefined)?.code;
    if (code === "LOGIN_ACCOUNT_DISABLED") {
      return "该账号已被停用，请联系管理员。";
    }
    if (code === "LOGIN_NO_ACTIVE_MEMBERSHIP") {
      return "未找到可用的租户成员关系（可能未加入租户、成员已禁用，或数据与程序版本不一致）。请联系管理员在「成员管理」中核对成员关系与数据库迁移。";
    }
    return "该账号无管理后台权限（例如仅为成员角色），或已被禁用。";
  }
  if (status === 404) {
    return "当前无法完成登录，请稍后再试或联系管理员。";
  }
  if (status != null && status >= 500) {
    return "服务暂时繁忙，请稍后再试。";
  }
  if (err.code === "ERR_NETWORK" && err.response == null) {
    return BACKEND_UNREACHABLE_MESSAGE;
  }
  return "登录未成功，请稍后重试。";
}
