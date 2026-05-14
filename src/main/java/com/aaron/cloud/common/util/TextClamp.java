package com.aaron.cloud.common.util;

/** 主模型上下文拼装等场景下的按字符数截断（UTF-16 单元），避免单条注入过长。 */
public final class TextClamp {

    private TextClamp() {}

    /**
     * 去首尾空白后截断；{@code maxChars <= 0} 返回空串；超出时保留前段并以 {@code …} 结尾（占 1 字符位）。
     */
    public static String ellipsis(String raw, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String t = raw.strip();
        if (t.length() <= maxChars) {
            return t;
        }
        if (maxChars <= 1) {
            return "…";
        }
        return t.substring(0, maxChars - 1) + "…";
    }
}
