package com.aaron.cloud.chat.websearch;

/** 规范化火山方舟 Bot Chat Completions 完整 URL（与官方 {@code …/api/v3/bots/chat/completions} 对齐）。 */
public final class ArkBotChatCompletionsUrl {

    private ArkBotChatCompletionsUrl() {}

    public static String normalize(String rawBase) {
        if (rawBase == null) {
            return "";
        }
        String t = rawBase.trim();
        if (t.isEmpty()) {
            return t;
        }
        if (t.contains("chat/completions")) {
            return t;
        }
        if (!t.endsWith("/")) {
            t = t + "/";
        }
        return t + "chat/completions";
    }
}
