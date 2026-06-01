package com.aaron.cloud.chat.support;

import java.util.Locale;
import java.util.Set;

/** 上传附件在 API/UI 中的展示分类（按扩展名，与 {@link com.aaron.cloud.chat.ChatAttachmentUploadService} 白名单一致）。 */
public final class ChatAttachmentFileKind {

    public static final String IMAGE = "image";
    public static final String DOCUMENT = "document";

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private ChatAttachmentFileKind() {}

    public static String resolve(String fileName) {
        String ext = extensionOf(fileName);
        if (ext != null && IMAGE_EXTENSIONS.contains(ext)) {
            return IMAGE;
        }
        return DOCUMENT;
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
