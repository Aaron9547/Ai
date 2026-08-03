package com.aaron.cloud.chat.support;

import com.aaron.cloud.common.document.ExtractedDocumentTexts;
import lombok.extern.slf4j.Slf4j;

/** 对话附件解析正文写入日志（截断，避免刷屏）。 */
@Slf4j
public final class ChatAttachmentExtractLog {

    private static final int LOG_PREVIEW_MAX = 4000;

    private ChatAttachmentExtractLog() {}

    public static void logAfterSave(
            long attachmentId, String fileName, String mimeType, String kind, boolean textExtracted, String text) {
        if (ExtractedDocumentTexts.isPlaceholder(text)) {
            log.info(
                    "[对话附件] 已入库 id={} 文件={} MIME={} 类型={} textExtracted=false（无解析正文）",
                    attachmentId,
                    fileName,
                    mimeType,
                    kind);
            return;
        }
        log.info(
                "[对话附件] 已入库 id={} 文件={} MIME={} 类型={} textExtracted={} 字数={} 解析正文=\n{}",
                attachmentId,
                fileName,
                mimeType,
                kind,
                textExtracted,
                text.length(),
                preview(text));
    }

    private static String preview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= LOG_PREVIEW_MAX) {
            return text;
        }
        return text.substring(0, LOG_PREVIEW_MAX) + "\n…（日志已截断，全文 " + text.length() + " 字）";
    }
}
