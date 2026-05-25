package com.aaron.cloud.common.security;

import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 账号编号、邮箱/手机规范化及注册途径推断。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserAccountProfileSupport {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_CN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ACCOUNT_NO_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    public static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        return s.isEmpty() ? null : s;
    }

    public static boolean isValidEmail(String normalized) {
        return normalized != null && !normalized.isBlank() && EMAIL.matcher(normalized).matches();
    }

    /** 国内 11 位手机号；其它格式原样 trim（后续可扩展 E.164）。 */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11 && PHONE_CN.matcher(digits).matches()) {
            return digits;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    public static boolean isValidCnPhone(String normalized) {
        return normalized != null && PHONE_CN.matcher(normalized).matches();
    }

    /** 对外账号编号：{@code U} + 12 位无歧义字符。 */
    public static String generateAccountNo() {
        char[] buf = new char[13];
        buf[0] = 'U';
        for (int i = 1; i < buf.length; i++) {
            buf[i] = ACCOUNT_NO_ALPHABET[RANDOM.nextInt(ACCOUNT_NO_ALPHABET.length)];
        }
        return new String(buf);
    }

    public static UserRegistrationChannel inferChannel(
            String loginName, String email, String phone, boolean adminCreated) {
        if (adminCreated) {
            return UserRegistrationChannel.ADMIN;
        }
        if (phone != null && !phone.isBlank()) {
            return UserRegistrationChannel.PHONE;
        }
        if (email != null && !email.isBlank()) {
            return UserRegistrationChannel.EMAIL;
        }
        if (loginName != null && loginName.contains("@")) {
            return UserRegistrationChannel.EMAIL;
        }
        return UserRegistrationChannel.USERNAME;
    }
}
