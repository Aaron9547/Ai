package com.aaron.cloud.identity.open;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 租户 {@code AUTH_REGISTER_VERIFICATION_JSON} 结构（验证码参数；邮件通道见消息中心）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRegisterVerificationConfig {

    private int codeLength = 6;
    private int codeTtlSeconds = 600;
    private int sendCooldownSeconds = 60;
}
