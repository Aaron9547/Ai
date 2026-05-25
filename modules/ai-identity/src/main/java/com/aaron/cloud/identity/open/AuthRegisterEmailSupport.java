package com.aaron.cloud.identity.open;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 注册验证码邮件通道是否可用于真实发信。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthRegisterEmailSupport {

    public static boolean isDeliveryReady(AuthRegisterVerificationConfig.EmailChannel email) {
        if (email == null) {
            return false;
        }
        return notBlank(email.getSmtpHost())
                && notBlank(email.getFrom())
                && notBlank(email.getUsername())
                && notBlank(email.getPassword());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
