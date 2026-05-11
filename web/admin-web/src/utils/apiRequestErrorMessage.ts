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

/**
 * 将 API 调用异常转为面向运营/用户的简短说明。
 * Axios 在 4xx/5xx 时 {@link Error#message} 常为「Request failed with status code …」，须读响应体 {@code message}。
 */
export function apiRequestErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const fromBody = messageFromResponseData(err.response?.data);
    if (fromBody) {
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
