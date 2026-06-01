package com.aaron.cloud.chat.support;

/** 对话附件抽取结果相关常量。 */
public final class ChatAttachmentTexts {

    /** Tika 未抽出可读文本时写入库表的占位文案（仍会注入模型上下文）。 */
    public static final String EMPTY_EXTRACT_PLACEHOLDER =
            "（未能从该文件中解析出可读文本；若为扫描件或图片请改用 OCR 管线。）";

    private ChatAttachmentTexts() {}

    public static boolean isPlaceholder(String extractedText) {
        return EMPTY_EXTRACT_PLACEHOLDER.equals(extractedText);
    }
}
