package com.aaron.cloud.chat.support;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class ChatAttachmentHttpSupport {

    private ChatAttachmentHttpSupport() {}

    public static MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** 图片、PDF 等可在浏览器内联预览，其余建议下载打开。 */
    public static boolean preferInline(String mimeType, String fileName) {
        if (mimeType != null) {
            String m = mimeType.toLowerCase(Locale.ROOT);
            if (m.startsWith("image/")) {
                return true;
            }
            if (MediaType.APPLICATION_PDF_VALUE.equals(m)) {
                return true;
            }
            if (m.startsWith("text/")) {
                return true;
            }
        }
        String ext = extensionOf(fileName);
        if (ext == null) {
            return false;
        }
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "pdf", "txt", "md", "csv" -> true;
            default -> false;
        };
    }

    public static HttpHeaders contentHeaders(String fileName, String mimeType, long contentLength) {
        boolean inline = preferInline(mimeType, fileName);
        String safeName = fileName == null || fileName.isBlank() ? "attachment" : fileName;
        ContentDisposition disposition =
                (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(safeName, StandardCharsets.UTF_8)
                        .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(resolveMediaType(mimeType));
        headers.setContentLength(contentLength);
        return headers;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
