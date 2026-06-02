package com.aaron.cloud.common.api.ports;

import java.util.Optional;

/**
 * 图片字节 OCR / 文字抽取（可选云端回退）。
 *
 * <p>本机 OCR 由 {@link com.aaron.cloud.common.document.LocalChainedImageOcr} 在
 * {@link com.aaron.cloud.common.document.TikaDocumentTextExtractor} 内执行（RapidOCR → Tesseract）。
 * 本端口由 {@code ai-model} 提供租户视觉大模型回退；对话附件默认不调用。
 */
public interface ImageTextOcrPort {

    Optional<String> tryExtract(long tenantId, byte[] bytes, String mimeType);
}
