/** 与后端 {@code ChatSendPayload.responseLocale}、用户端 i18n 对齐 */
export type ChatResponseLocale = "zh-CN" | "en-US";

export function toChatResponseLocale(i18nLocale: string): ChatResponseLocale {
  return i18nLocale.startsWith("en") ? "en-US" : "zh-CN";
}
