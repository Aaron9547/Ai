package com.aaron.cloud.notification.message;

import java.util.Map;
import java.util.regex.Pattern;

public final class MessageTemplateSupport {

    private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z][^>]*>");

    private MessageTemplateSupport() {}

    public static String applyTemplate(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String value = e.getValue() == null ? "" : normalizeEscapes(e.getValue());
                out = out.replace("{" + e.getKey() + "}", value);
            }
        }
        return normalizeEscapes(out);
    }

    /**
     * 将模板/变量中常见的字面量转义（如 JSON 或管理端粘贴的 {@code \\n}）还原为真实换行。
     */
    public static String normalizeEscapes(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String s = text;
        s = s.replace("\\r\\n", "\n");
        s = s.replace("\\n", "\n");
        s = s.replace("\\r", "\n");
        s = s.replace("\\t", "\t");
        return s;
    }

    public static boolean looksLikeHtml(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String t = body.trim().toLowerCase();
        if (t.startsWith("<!doctype html") || t.startsWith("<html")) {
            return true;
        }
        return HTML_TAG.matcher(body).find();
    }

    /** 纯文本转简易 HTML（换行 → {@code <br/>}），供邮件客户端排版。 */
    public static String plainTextToSimpleHtml(String plain) {
        String normalized = normalizeEscapes(plain == null ? "" : plain);
        StringBuilder sb = new StringBuilder(128 + normalized.length() * 2);
        sb.append(
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/></head>"
                        + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
                        + "line-height:1.6;color:#222;\">");
        for (String line : normalized.split("\n", -1)) {
            sb.append(escapeHtml(line)).append("<br/>\n");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /** HTML 模板的纯文本备选（用于 multipart/alternative）。 */
    public static String htmlToPlainFallback(String html) {
        if (html == null) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</p>", "\n\n");
        s = s.replaceAll("<[^>]+>", "");
        return normalizeEscapes(s).trim();
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
