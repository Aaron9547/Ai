package com.aaron.cloud.identity.open;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 租户 {@code AUTH_REGISTER_VERIFICATION_JSON} 结构；管理端「外观与模型调用」配置（仅邮件）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthRegisterVerificationConfig {

    private int codeLength = 6;
    private int codeTtlSeconds = 600;
    private int sendCooldownSeconds = 60;

    private EmailChannel email = new EmailChannel();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmailChannel {
        private String smtpHost = "";
        private int smtpPort = 465;
        private String username = "";
        private String password = "";
        private String from = "";
        private boolean ssl = true;
        private String subjectTemplate = "【{tenantName}】注册验证码";
        private String bodyTemplate =
                """
                您好，

                您正在注册账号，验证码为：{code}

                验证码 {ttlMinutes} 分钟内有效，请勿泄露给他人。如非本人操作，请忽略此邮件。
                """;
    }
}
