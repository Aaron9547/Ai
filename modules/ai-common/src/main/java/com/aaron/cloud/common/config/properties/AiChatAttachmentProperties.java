package com.aaron.cloud.common.config.properties;

import java.io.File;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 会话附件原始文件落盘（不入库），供按 attachment id 读回字节流（如上传 Coze）。 */
@Data
@ConfigurationProperties(prefix = "ai.chat.attachment")
public class AiChatAttachmentProperties {

    /**
     * 根目录；实际路径为 {@code {binDir}/{tenantId}/{conversationId}/{attachmentId}}（无扩展名，纯数字路径段）。
     */
    private String binDir = System.getProperty("user.dir") + File.separator + "data" + File.separator + "chat-attachment-bin";

    /**
     * 图片附件在本机 OCR（RapidOCR/Tesseract）质量不足时，是否回退租户视觉大模型 OCR（需配置 VISION 或 vl/vision 模型）。
     */
    private boolean visionOcrFallback = true;
}
