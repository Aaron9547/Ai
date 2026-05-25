import axios from "axios";

const BACKEND_CORS_BLOCKED_MESSAGE =
  "无法完成登录：当前访问方式不受支持。请通过管理员提供的链接打开页面，或联系管理员。";

const BACKEND_UNREACHABLE_MESSAGE =
  "无法连接服务，请检查网络后重试，或通过管理员提供的访问地址打开页面。";

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

function errorCode(err: unknown): string | undefined {
  if (!axios.isAxiosError(err)) {
    return undefined;
  }
  return (err.response?.data as { code?: string } | undefined)?.code;
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
    return "邮箱或密码不正确。";
  }
  if (status === 403) {
    const code = errorCode(err);
    if (code === "LOGIN_ACCOUNT_DISABLED") {
      return "该账号已被停用，请联系管理员。";
    }
    if (code === "LOGIN_NO_ACTIVE_MEMBERSHIP") {
      return "账号尚未开通使用权限，请联系管理员。";
    }
    return "暂无使用权限，请联系管理员。";
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
  const code = errorCode(err);
  if (status === 409 || code === "LOGIN_NAME_CONFLICT") {
    return "该邮箱已被注册。";
  }
  if (code === "REGISTER_EMAIL_INVALID") {
    return "请输入有效的邮箱地址。";
  }
  if (code === "REGISTER_PASSWORD_WEAK") {
    return "密码至少 8 位，且需同时包含字母与数字。";
  }
  if (code === "REGISTER_CODE_INVALID") {
    return status === 410 ? "验证码已过期，请重新获取。" : "验证码不正确，请检查后重试。";
  }
  if (status === 400) {
    return "请检查邮箱、验证码与密码是否符合要求。";
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

/** 发送注册验证码失败时的用户可见说明。 */
export function openAuthSendCodeErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err)) {
    return "验证码发送失败，请稍后重试。";
  }
  if (isBackendCorsBlocked(err)) {
    return BACKEND_CORS_BLOCKED_MESSAGE;
  }
  const status = err.response?.status;
  const code = errorCode(err);
  if (status === 409 || code === "LOGIN_NAME_CONFLICT") {
    return "该邮箱已被注册，请直接登录。";
  }
  if (code === "REGISTER_EMAIL_INVALID") {
    return "请输入有效的邮箱地址。";
  }
  if (status === 403) {
    return "当前环境已关闭开放注册。";
  }
  if (status === 429) {
    return "发送过于频繁，请稍后再试。";
  }
  if (status != null && status >= 500) {
    return "服务暂时繁忙，请稍后再试。";
  }
  if (err.code === "ERR_NETWORK" && err.response == null) {
    return BACKEND_UNREACHABLE_MESSAGE;
  }
  return "验证码发送失败，请稍后重试。";
}
