package com.aaron.cloud.identity.open;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** C 端邮箱注册：格式校验与规范化（小写 trim，作为 {@code login_name} 落库）。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenRegistrationEmailSupport {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase();
    }

    public static boolean isValid(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        if (normalizedEmail.length() > 128) {
            return false;
        }
        return EMAIL.matcher(normalizedEmail).matches();
    }

    /** 密码至少 8 位，且同时包含字母与数字。 */
    public static boolean isStrongEnoughPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return false;
    }
}
