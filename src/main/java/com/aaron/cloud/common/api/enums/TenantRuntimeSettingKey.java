package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户级运行时系统配置项（存 {@code ten_runtime_setting.setting_key}）；扩展能力时在此新增枚举，禁止在业务分支中写死键名字符串。
 */
@Getter
@RequiredArgsConstructor
public enum TenantRuntimeSettingKey {
    /** C 端 {@code POST /open/v1/auth/register} 是否允许（按注册目标租户判断） */
    AUTH_OPEN_REGISTRATION(
            "AUTH_OPEN_REGISTRATION", "允许 C 端用户在本租户下自助注册（MEMBER 角色）", SettingValueKind.BOOLEAN, true);

    @EnumValue
    private final String storage;

    private final String descriptionZh;
    private final SettingValueKind valueKind;
    private final boolean defaultBoolean;

    public String defaultValueText() {
        return defaultBoolean ? "true" : "false";
    }

    public static Optional<TenantRuntimeSettingKey> fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim();
        return Arrays.stream(values()).filter(k -> k.storage.equalsIgnoreCase(t)).findFirst();
    }

    public enum SettingValueKind {
        BOOLEAN
    }
}
