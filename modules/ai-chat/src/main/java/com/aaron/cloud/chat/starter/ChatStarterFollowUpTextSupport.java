package com.aaron.cloud.chat.starter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 「猜你想问」展示文案校验：过滤联网检索问句、画像摘录等不可作 chip 的文本。 */
final class ChatStarterFollowUpTextSupport {

    /** 与 {@link ChatStarterFollowUpService} 生成规范一致（8～36 字），略放宽供运营池。 */
    static final int DISPLAY_MAX_LEN = 80;

    private ChatStarterFollowUpTextSupport() {}

    static boolean isDisplayableChip(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.isEmpty() || t.length() > DISPLAY_MAX_LEN) {
            return false;
        }
        if (t.startsWith("{") || t.startsWith("[")) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (t.contains("今日最新资讯")
                || t.contains("与以下用户兴趣相关")
                || t.contains("跨会话最近输入摘录")
                || t.contains("【长期记忆")
                || lower.contains("memory_abstract")) {
            return false;
        }
        return true;
    }

    static List<String> filterQuestions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String q : raw) {
            if (isDisplayableChip(q)) {
                out.add(q.trim());
            }
        }
        return out;
    }
}
