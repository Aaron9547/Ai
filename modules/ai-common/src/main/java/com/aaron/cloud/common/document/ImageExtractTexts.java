package com.aaron.cloud.common.document;

/** 图片正文抽取结果合并（本机 OCR / Tika / 视觉模型等多路）。 */
public final class ImageExtractTexts {

    private ImageExtractTexts() {}

    /**
     * 在两条非空抽取结果中保留更完整的一条（以字符数为准；占位文案视为无效）。
     */
    public static String pickRicher(String primary, String secondary) {
        String a = primary == null ? "" : primary.trim();
        String b = secondary == null ? "" : secondary.trim();
        if (ExtractedDocumentTexts.isPlaceholder(a)) {
            a = "";
        }
        if (ExtractedDocumentTexts.isPlaceholder(b)) {
            b = "";
        }
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return b.length() > a.length() ? b : a;
    }
}
