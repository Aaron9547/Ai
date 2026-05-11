import axios from "axios";

const BACKEND_CORS_BLOCKED_MESSAGE =
  "当前页面地址或端口与后端允许的来源不一致，请求被浏览器拦截，并非登录名或密码错误。请使用约定的本地地址与端口，或请管理员扩展允许的来源。";

const BACKEND_UNREACHABLE_MESSAGE =
  "无法连上服务：请确认后端已启动；若本页地址或端口与后端配置不一致，也会出现此提示（不一定是账号问题）。";

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

/** 开放认证登录失败时的用户可见说明。 */
export function openAuthLoginErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err)) {
    return "登录失败，请稍后重试。";
  }
  if (isBackendCorsBlocked(err)) {
    return BACKEND_CORS_BLOCKED_MESSAGE;
  }
  const status = err.response?.status;
  if (status === 401) {
    return "登录名或密码不正确。";
  }
  if (status === 403) {
    const code = (err.response?.data as { code?: string } | undefined)?.code;
    if (code === "LOGIN_ACCOUNT_DISABLED") {
      return "该账号已被停用，请联系管理员。";
    }
    if (code === "LOGIN_NO_ACTIVE_MEMBERSHIP") {
      return "账号未激活或未加入租户，请联系管理员。";
    }
    return "账号未激活或无租户权限。";
  }
  if (status != null && status >= 500) {
    return "服务暂时繁忙，请稍后再试。";
  }
  if (err.code === "ERR_NETWORK" && err.response == null) {
    return BACKEND_UNREACHABLE_MESSAGE;
  }
  return "登录失败，请稍后重试。";
}

/** 开放认证注册失败时的用户可见说明。 */
export function openAuthRegisterErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err)) {
    return "注册失败，请稍后重试。";
  }
  if (isBackendCorsBlocked(err)) {
    return BACKEND_CORS_BLOCKED_MESSAGE;
  }
  const status = err.response?.status;
  if (status === 409) {
    return "该登录名已被占用。";
  }
  if (status === 400) {
    return "请检查登录名长度与密码强度。";
  }
  if (status === 403) {
    return "当前环境已关闭开放注册。";
  }
  if (status != null && status >= 500) {
    return "服务暂时繁忙，请稍后再试。";
  }
  if (err.code === "ERR_NETWORK" && err.response == null) {
    return BACKEND_UNREACHABLE_MESSAGE;
  }
  return "注册失败，请稍后重试。";
}
