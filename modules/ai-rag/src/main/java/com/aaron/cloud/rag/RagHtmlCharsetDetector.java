package com.aaron.cloud.rag;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 HTTP 头与 HTML 前部嗅探 charset（北方网/高校站常见 GBK/GB2312）。 */
public final class RagHtmlCharsetDetector {

    private static final Pattern META_CHARSET =
            Pattern.compile(
                    "<meta[^>]+charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)[\"']?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern META_HTTP_EQUIV =
            Pattern.compile(
                    "<meta[^>]+http-equiv\\s*=\\s*[\"']?content-type[\"']?[^>]+content\\s*=\\s*[\"'][^\"']*charset\\s*=\\s*([a-zA-Z0-9_-]+)",
                    Pattern.CASE_INSENSITIVE);

    private RagHtmlCharsetDetector() {}

    public static Charset resolve(String contentTypeHeader, byte[] body) {
        Charset fromHeader = fromContentTypeHeader(contentTypeHeader);
        if (fromHeader != null && !isGenericUtf8(fromHeader)) {
            return fromHeader;
        }
        Charset fromHtml = fromHtmlBytes(body);
        if (fromHtml != null) {
            return fromHtml;
        }
        return fromHeader != null ? fromHeader : StandardCharsets.UTF_8;
    }

    private static Charset fromContentTypeHeader(String ct) {
        if (ct == null || ct.isBlank()) {
            return null;
        }
        String lower = ct.toLowerCase();
        int idx = lower.indexOf("charset=");
        if (idx < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = ct.substring(idx + 8).split("[;\\s]")[0].trim().replace("\"", "").replace("'", "");
        return safeCharset(name);
    }

    private static Charset fromHtmlBytes(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        if (body.length >= 3 && body[0] == (byte) 0xEF && body[1] == (byte) 0xBB && body[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        int len = Math.min(body.length, 8192);
        String ascii = StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(body, 0, len)).toString();
        Matcher m1 = META_CHARSET.matcher(ascii);
        if (m1.find()) {
            Charset cs = safeCharset(m1.group(1));
            if (cs != null) {
                return cs;
            }
        }
        Matcher m2 = META_HTTP_EQUIV.matcher(ascii);
        if (m2.find()) {
            Charset cs = safeCharset(m2.group(1));
            if (cs != null) {
                return cs;
            }
        }
        if (looksLikeGbk(body, len)) {
            return Charset.forName("GBK");
        }
        return null;
    }

    /** 无 meta 时：GBK 中文页在 UTF-8 解码下会出现大量 0xC0-0xFF 连续字节特征。 */
    private static boolean looksLikeGbk(byte[] body, int len) {
        int high = 0;
        for (int i = 0; i < len; i++) {
            int b = body[i] & 0xFF;
            if (b >= 0x81 && b <= 0xFE) {
                high++;
            }
        }
        return high > len / 10;
    }

    private static boolean isGenericUtf8(Charset cs) {
        return StandardCharsets.UTF_8.equals(cs) || "UTF8".equalsIgnoreCase(cs.name());
    }

    private static Charset safeCharset(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim();
        if ("gb2312".equalsIgnoreCase(n) || "gb_2312".equalsIgnoreCase(n)) {
            return Charset.forName("GBK");
        }
        try {
            return Charset.forName(n);
        } catch (Exception e) {
            return null;
        }
    }
}
