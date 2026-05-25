import axios from "axios";

/**
 * 从后端 JSON 错误体（如 {@link com.aaron.cloud.common.web.ApiErrorResponse}）解析用户可见说明。
 */
function messageFromResponseData(data: unknown): string {
  if (data == null) {
    return "";
  }
  if (typeof data === "string") {
    const t = data.trim();
    return t.length > 0 ? t.slice(0, 500) : "";
  }
  if (typeof data === "object" && data !== null && "message" in data) {
    const m = (data as { message?: unknown }).message;
    if (typeof m === "string") {
      const t = m.trim();
      if (t.length > 0) {
        return t;
      }
    }
  }
  return "";
}

const API_ERROR_ZH_BY_MESSAGE: Record<string, string> = {
  "granted rpm sum exceeds access party total_rpm_cap":
    "各接口 RPM 之和超过接入方总上限，请减少接口或调低 RPM",
  "granted rpm exceeds endpoint global_rpm_cap":
    "单接口 RPM 超过该接口的全局上限",
};

/** 已知 {@code ApiErrorResponse.code} 的中文简述（与后端 GlobalExceptionHandler 对齐）。 */
const API_ERROR_ZH_BY_CODE: Record<string, string> = {
  TENANT_MEMBER_INACTIVE: "该成员已不在册，无法执行此操作",
  TENANT_MEMBER_ALREADY_ACTIVE: "该用户在本租户已有在册成员身份",
  USER_NOT_IN_CURRENT_TENANT: "用户不在当前租户",
  LOGIN_NAME_CONFLICT: "该登录名已被占用",
  FORBIDDEN: "无权执行此操作",
};

function errorCodeFromResponseData(data: unknown): string {
  if (typeof data === "object" && data !== null && "code" in data) {
    const c = (data as { code?: unknown }).code;
    return typeof c === "string" ? c.trim() : "";
  }
  return "";
}

/**
 * 将 API 调用异常转为面向运营/用户的简短说明。
 * Axios 在 4xx/5xx 时 {@link Error#message} 常为「Request failed with status code …」，须读响应体 {@code message}。
 */
export function apiRequestErrorMessage(
  err: unknown,
  fallback: string,
  localeTag: string = "zh-CN",
): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data;
    const code = errorCodeFromResponseData(data);
    const useZh = !String(localeTag || "zh-CN").toLowerCase().startsWith("en");
    if (useZh && code && API_ERROR_ZH_BY_CODE[code]) {
      return API_ERROR_ZH_BY_CODE[code]!;
    }
    const fromBody = messageFromResponseData(data);
    if (fromBody) {
      if (useZh && API_ERROR_ZH_BY_MESSAGE[fromBody]) {
        return API_ERROR_ZH_BY_MESSAGE[fromBody]!;
      }
      return fromBody;
    }
    if (err.code === "ERR_NETWORK" && err.response == null) {
      return "无法连接服务，请确认后端已启动且网络正常。";
    }
    return fallback;
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
}
