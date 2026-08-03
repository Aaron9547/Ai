package com.aaron.cloud.common.web;

/**
 * 将 {@code sec_user_account.last_login_region}（见 {@link LoginRegionResolver}）转为联网检索可用的地域短语。
 */
public final class LoginRegionSearchPhrase {

    private LoginRegionSearchPhrase() {}

    /**
     * @return 非空时末尾带空格，便于模板拼接；无地域信息时返回空串
     */
    public static String toSearchPhrase(String lastLoginRegion) {
        if (lastLoginRegion == null || lastLoginRegion.isBlank()) {
            return "";
        }
        String raw = lastLoginRegion.trim();
        if ("—".equals(raw)) {
            return "";
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length == 1) {
            String country = countryLabel(parts[0].trim());
            return country.isEmpty() ? "" : country + " ";
        }
        StringBuilder sb = new StringBuilder();
        String country = countryLabel(parts[0].trim());
        if (!country.isEmpty()) {
            sb.append(country);
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(parts[1].trim());
        }
        if (parts.length > 2 && !parts[2].isBlank()) {
            String city = parts[2].trim();
            if (!city.equalsIgnoreCase(parts.length > 1 ? parts[1].trim() : "")) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(city);
            }
        }
        return sb.isEmpty() ? "" : sb + " ";
    }

    private static String countryLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String cc = code.trim().toUpperCase();
        if (cc.length() > 2) {
            cc = cc.substring(0, 2);
        }
        return switch (cc) {
            case "CN" -> "中国";
            case "HK" -> "香港";
            case "MO" -> "澳门";
            case "TW" -> "台湾";
            default -> cc.length() == 2 && Character.isLetter(cc.charAt(0)) && Character.isLetter(cc.charAt(1))
                    ? cc
                    : code.trim();
        };
    }
}
