package com.aaron.cloud.common.api.ports;

import java.util.Optional;

/**
 * 图片字节 OCR / 文字抽取（租户视觉大模型，OpenAI 兼容 {@code image_url}）。
 *
 * <p>本机 OCR 由 {@link com.aaron.cloud.common.document.LocalChainedImageOcr} 在
 * {@link com.aaron.cloud.common.document.TikaDocumentTextExtractor} 内执行；对话图片附件在本机结果质量不足时
 * 由 {@link com.aaron.cloud.chat.ChatAttachmentUploadService} 调用本端口回退。
 */
public interface ImageTextOcrPort {

    Optional<String> tryExtract(long tenantId, byte[] bytes, String mimeType);
}
