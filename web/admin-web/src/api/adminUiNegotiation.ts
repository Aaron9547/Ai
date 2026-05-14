import { AxiosHeaders } from "axios";

/** 管理端与后端 {@code AdminUiLocaleResolver} 对齐的界面语言 */
export type AdminUiLocaleTag = "zh-CN" | "en-US";

/**
 * 管理端「显式语言」请求：{@code Accept-Language} + {@code ?lang=} + 禁用缓存，避免网关丢头或 CDN 旧数据。
 *
 * <p>其它需要同样协商策略的 admin GET 可复用本方法与 `adminUiNegotiationParams`。
 */
export function adminUiNegotiationHeaders(acceptLanguage: AdminUiLocaleTag): AxiosHeaders {
  const headers = new AxiosHeaders();
  headers.set("Accept-Language", acceptLanguage, true);
  headers.set("Cache-Control", "no-cache", true);
  return headers;
}

export function adminUiNegotiationParams(acceptLanguage: AdminUiLocaleTag): { lang: AdminUiLocaleTag; _ts: number } {
  return { lang: acceptLanguage, _ts: Date.now() };
}
