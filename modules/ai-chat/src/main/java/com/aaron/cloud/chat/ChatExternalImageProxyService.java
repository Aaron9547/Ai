package com.aaron.cloud.chat;

import com.aaron.cloud.common.outbound.SafeOutboundUrlGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 服务端拉取对话 Markdown 外链图片，供 C 端同源代理展示与分享长图内联（绕过浏览器 CORS / 防盗链）。
 */
@Service
@Slf4j
public class ChatExternalImageProxyService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public record ProxiedImage(byte[] body, String contentType, String fileName) {}

    public ProxiedImage fetch(String rawUrl) throws IOException {
        URI uri = SafeOutboundUrlGuard.requireHttpOrHttps(rawUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        connection.setRequestProperty("Referer", refererFor(uri));
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new IOException("HTTP " + code + " without body");
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            byte[] body = readLimited(stream, MAX_BYTES);
            String contentType = normalizeContentType(connection.getContentType(), body);
            if (!contentType.startsWith("image/")) {
                throw new IllegalArgumentException("not an image");
            }
            String fileName = fileNameFrom(uri);
            return new ProxiedImage(body, contentType, fileName);
        } finally {
            connection.disconnect();
        }
    }

    private static String refererFor(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        int port = uri.getPort();
        if (port > 0 && port != 80 && port != 443) {
            return uri.getScheme() + "://" + uri.getHost() + ":" + port + "/";
        }
        return uri.getScheme() + "://" + uri.getHost() + "/";
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) >= 0) {
            if (n == 0) {
                continue;
            }
            total += n;
            if (total > maxBytes) {
                throw new IOException("response too large");
            }
            buf.write(chunk, 0, n);
        }
        if (total == 0) {
            throw new IOException("empty response");
        }
        return buf.toByteArray();
    }

    private static String normalizeContentType(String header, byte[] body) {
        if (header != null && !header.isBlank()) {
            String ct = header.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (ct.startsWith("image/")) {
                return ct;
            }
        }
        return sniffImageContentType(body);
    }

    private static String sniffImageContentType(byte[] body) {
        if (body.length >= 3 && (body[0] & 0xff) == 0xff && (body[1] & 0xff) == 0xd8 && (body[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (body.length >= 8
                && body[0] == (byte) 0x89
                && body[1] == 0x50
                && body[2] == 0x4e
                && body[3] == 0x47) {
            return "image/png";
        }
        if (body.length >= 6) {
            String head = new String(body, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if (head.startsWith("GIF87a") || head.startsWith("GIF89a")) {
                return "image/gif";
            }
        }
        if (body.length >= 12) {
            String riff = new String(body, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            String webp = new String(body, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return "image/webp";
            }
        }
        return "application/octet-stream";
    }

    private static String fileNameFrom(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "image";
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank()) {
            return "image";
        }
        return name;
    }
}
