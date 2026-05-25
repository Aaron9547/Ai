package com.aaron.cloud.common.api.enums.identity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 账号首次创建途径（落库 {@code sec_user_account.registration_channel}）。 */
@Getter
@RequiredArgsConstructor
public enum UserRegistrationChannel {
    /** 管理端手工创建 */
    ADMIN("ADMIN"),
    /** 邮箱验证码 / 邮箱+密码注册 */
    EMAIL("EMAIL"),
    /** 短信验证码注册（预留） */
    PHONE("PHONE"),
    /** 自定义登录名注册 */
    USERNAME("USERNAME"),
    /** 第三方 OAuth（预留） */
    OAUTH("OAUTH");

    @EnumValue private final String code;
}
