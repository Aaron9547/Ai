package com.aaron.cloud.chat.websearch.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** 联网检索问句规范化（精确缓存键、语义索引与会话内复用比对）。 */
public final class WebSearchQueryNormalizer {

    private static final Pattern COLLAPSE_WS = Pattern.compile("\\s+");
    private static final Pattern STRIP_PUNCT =
            Pattern.compile("[?？!！。,，、;；:：\"'「」『』（）()【】\\[\\]《》<>~～·…—\\-]+");

    private WebSearchQueryNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replace('\uFEFF', ' ').replace('\u200B', ' ');
        s = STRIP_PUNCT.matcher(s).replaceAll(" ");
        s = COLLAPSE_WS.matcher(s).replaceAll(" ").trim();
        return s;
    }

    public static String sha256Hex(String normalized) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
