import axios from "axios";

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

/** 会话归属切换后（如退出登录）访问原用户会话时后端返回 400 + 固定文案。 */
export function isConversationNotFoundHttpError(err: unknown): boolean {
  if (!axios.isAxiosError(err) || err.response?.status !== 400) {
    return false;
  }
  return messageFromResponseData(err.response?.data) === "conversation not found";
}

/** 将 API 异常转为用户可见说明（优先读响应体 {@code message}，而非 Axios 默认英文）。 */
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
