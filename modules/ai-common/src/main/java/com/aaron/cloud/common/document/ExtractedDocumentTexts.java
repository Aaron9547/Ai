package com.aaron.cloud.common.document;

/** 文档正文抽取结果相关常量（对话附件、RAG 等共用）。 */
public final class ExtractedDocumentTexts {

    /** Tika 未抽出可读文本时的占位文案（仍会注入模型或入库流程）。 */
    public static final String EMPTY_EXTRACT_PLACEHOLDER =
            "（未能从该文件中解析出可读文本；若为扫描件或图片请改用 OCR 管线。）";

    private ExtractedDocumentTexts() {}

    public static boolean isPlaceholder(String extractedText) {
        return EMPTY_EXTRACT_PLACEHOLDER.equals(extractedText);
    }
}
