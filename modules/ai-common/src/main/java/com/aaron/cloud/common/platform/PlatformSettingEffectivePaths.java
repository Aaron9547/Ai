package com.aaron.cloud.common.platform;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import java.nio.file.Path;

/** 平台参数中的本地落盘路径解析（空值回退进程默认目录）。 */
public final class PlatformSettingEffectivePaths {

    private PlatformSettingEffectivePaths() {}

    public static Path adminBrandLogoStorageDir(PlatformSettingApplicationService platformSettings) {
        String raw = platformSettings.getEffectiveValueText(PlatformSettingKey.ADMIN_BRAND_LOGO_STORAGE_DIR);
        if (raw == null || raw.isBlank()) {
            return Path.of(System.getProperty("user.dir", "."), "var", "admin-brand-logos")
                    .toAbsolutePath()
                    .normalize();
        }
        return Path.of(raw.trim()).toAbsolutePath().normalize();
    }

    public static Path chatAttachmentBinDir(PlatformSettingApplicationService platformSettings) {
        String raw = platformSettings.getEffectiveValueText(PlatformSettingKey.CHAT_ATTACHMENT_BIN_DIR);
        if (raw == null || raw.isBlank()) {
            return Path.of(System.getProperty("user.dir", "."), "data", "chat-attachment-bin")
                    .toAbsolutePath()
                    .normalize();
        }
        return Path.of(raw.trim()).toAbsolutePath().normalize();
    }
}
