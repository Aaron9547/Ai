package com.aaron.cloud.common.config.properties;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端租户 LOGO 本地上传目录；文件经 {@code GET /open/v1/admin-brand-logos/{tenantCode}/{uuid}.ext} 匿名读取（供侧栏
 * {@code <img>}）。
 */
@Data
@ConfigurationProperties(prefix = "ai.admin.brand-logo")
public class AiAdminBrandLogoProperties {

    /**
     * 落盘根目录；空则使用 {@code ${user.dir}/var/admin-brand-logos}。须进程可写；多实例部署时应挂载共享卷或改对象存储方案。
     */
    private String storageDir = "";

    public Path resolvedStorageDir() {
        if (storageDir == null || storageDir.isBlank()) {
            return Paths.get(System.getProperty("user.dir", "."), "var", "admin-brand-logos").toAbsolutePath().normalize();
        }
        return Paths.get(storageDir.trim()).toAbsolutePath().normalize();
    }
}
