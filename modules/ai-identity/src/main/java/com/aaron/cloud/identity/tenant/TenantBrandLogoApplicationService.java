package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.config.properties.AiAdminBrandLogoProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 管理端侧栏 LOGO：上传落盘 + 按租户编码与文件名读取（供匿名 GET）。 */
@Service
@RequiredArgsConstructor
public class TenantBrandLogoApplicationService {

    public static final String PUBLIC_URL_PREFIX = "/open/v1/admin-brand-logos";

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_TENANT_CODE = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Pattern STORED_FILE =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|jpeg|gif|webp)$",
                    Pattern.CASE_INSENSITIVE);

    private final AiAdminBrandLogoProperties properties;

    public String store(MultipartFile file, String tenantCode) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logo 超过 2MB 上限");
        }
        String code = sanitizeTenantCode(tenantCode);
        String ext = extensionOf(file.getOriginalFilename(), file.getContentType());
        Path root = properties.resolvedStorageDir();
        Path dir = root.resolve(code);
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return PUBLIC_URL_PREFIX + "/" + code + "/" + fileName;
    }

    public Optional<ServedLogo> load(String tenantCode, String fileName) throws Exception {
        if (!STORED_FILE.matcher(fileName).matches()) {
            return Optional.empty();
        }
        fileName = fileName.toLowerCase(Locale.ROOT);
        String code = sanitizeTenantCode(tenantCode);
        Path root = properties.resolvedStorageDir();
        Path target = root.resolve(code).resolve(fileName).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        byte[] bytes = Files.readAllBytes(target);
        String extPart = fileName.substring(fileName.lastIndexOf('.') + 1);
        String ct =
                switch (extPart) {
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "webp" -> "image/webp";
                    default -> "application/octet-stream";
                };
        return Optional.of(new ServedLogo(bytes, ct));
    }

    private static String sanitizeTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantCode required");
        }
        String t = tenantCode.trim();
        if (!SAFE_TENANT_CODE.matcher(t).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid tenantCode");
        }
        return t;
    }

    private static String extensionOf(String originalName, String contentType) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                ext = originalName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (ext.isEmpty() && contentType != null) {
            ext =
                    switch (contentType.split(";")[0].trim().toLowerCase(Locale.ROOT)) {
                        case "image/png" -> "png";
                        case "image/jpeg" -> "jpg";
                        case "image/gif" -> "gif";
                        case "image/webp" -> "webp";
                        default -> "";
                    };
        }
        if (!ext.matches("png|jpg|jpeg|gif|webp")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "仅支持 png、jpg、jpeg、gif、webp 图片");
        }
        return ext;
    }

    public record ServedLogo(byte[] bytes, String contentType) {}
}
