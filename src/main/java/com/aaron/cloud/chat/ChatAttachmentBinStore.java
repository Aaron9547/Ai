package com.aaron.cloud.chat;

import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.platform.PlatformSettingEffectivePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话附件原始字节落盘：上传入库后按 {@code tenantId / conversationId / attachmentId} 写入，业务侧（如出差报销）再按 id 读回流上传
 * Coze，避免把 BLOB 放进 MySQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAttachmentBinStore {

    private final PlatformSettingApplicationService platformSettings;

    public void persist(long tenantId, long conversationId, long attachmentId, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        Path file = resolveFile(tenantId, conversationId, attachmentId);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public Optional<byte[]> load(long tenantId, long conversationId, long attachmentId) {
        Path file = resolveFile(tenantId, conversationId, attachmentId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            log.warn(
                    "[对话附件] 读取本地文件失败：租户 {}，会话 {}，附件 {}，路径 {}",
                    tenantId,
                    conversationId,
                    attachmentId,
                    file,
                    e);
            return Optional.empty();
        }
    }

    private Path resolveFile(long tenantId, long conversationId, long attachmentId) {
        Path base = PlatformSettingEffectivePaths.chatAttachmentBinDir(platformSettings);
        return base.resolve(Long.toString(tenantId)).resolve(Long.toString(conversationId)).resolve(Long.toString(attachmentId));
    }
}
