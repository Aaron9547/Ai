package com.aaron.cloud.chat.support;

import java.util.Locale;

/** 上传文件名规范化（剪贴板/拖拽可能无扩展名）。 */
public final class ChatAttachmentUploadFileNames {

    private ChatAttachmentUploadFileNames() {}

    public static String normalize(String original, String contentType) {
        String name = original == null ? "" : original.trim();
        if (name.isEmpty() || "blob".equalsIgnoreCase(name) || "upload".equalsIgnoreCase(name)) {
            return defaultNameForMime(contentType);
        }
        if (!hasExtension(name)) {
            String ext = extensionFromMime(contentType);
            if (ext != null) {
                return name + "." + ext;
            }
        }
        return name;
    }

    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1;
    }

    private static String defaultNameForMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "upload.bin";
        }
        String mime = contentType.toLowerCase(Locale.ROOT).trim();
        String ext = extensionFromMime(mime);
        if (mime.startsWith("image/")) {
            return "pasted-image." + (ext != null ? ext : "png");
        }
        if ("application/pdf".equals(mime)) {
            return "pasted.pdf";
        }
        if (mime.contains("word") || mime.contains("document")) {
            return "pasted.docx";
        }
        return "pasted-file." + (ext != null ? ext : "bin");
    }

    static String extensionFromMime(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT).trim()) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "application/pdf" -> "pdf";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "text/csv" -> "csv";
            default -> null;
        };
    }
}
