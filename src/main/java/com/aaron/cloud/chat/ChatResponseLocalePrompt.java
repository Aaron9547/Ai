package com.aaron.cloud.chat;

/**
 * 用户端 UI 语种与模型回复语种对齐：由请求 {@code responseLocale} 注入 system 提示。
 */
public final class ChatResponseLocalePrompt {

    public static final String ZH_CN = "zh-CN";
    public static final String EN_US = "en-US";

    private ChatResponseLocalePrompt() {}

    /** 缺省或未识别时按简体中文。 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZH_CN;
        }
        String t = raw.trim();
        if (t.startsWith("en") || EN_US.equalsIgnoreCase(t)) {
            return EN_US;
        }
        return ZH_CN;
    }

    public static boolean isEnglish(String responseLocale) {
        return EN_US.equals(normalize(responseLocale));
    }

    public static String baseAssistantPersona(String responseLocale) {
        return isEnglish(responseLocale) ? "You are the Ai platform assistant." : "你是 Ai 中台助手。";
    }

    /** 追加语种约束（与画像、RAG 等 system 段同条或紧随其后）。 */
    public static void appendLanguageDirective(StringBuilder sys, String responseLocale) {
        if (isEnglish(responseLocale)) {
            sys.append("\n\n【Response language】Reply in English. If the user explicitly asks for another language, follow the user.");
        } else {
            sys.append("\n\n【回复语种】请使用简体中文回复。若用户明确要求使用其他语言，则按用户要求。");
        }
    }
}
