package com.aaron.cloud.common.document;

import java.util.Locale;
import java.util.Set;

/** 上传文件在 API/UI 中的粗分类（按扩展名）。 */
public final class UploadedFileKind {

    public static final String IMAGE = "image";
    public static final String DOCUMENT = "document";

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    private UploadedFileKind() {}

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
