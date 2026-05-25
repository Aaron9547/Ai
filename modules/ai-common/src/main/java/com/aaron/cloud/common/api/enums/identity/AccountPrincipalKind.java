package com.aaron.cloud.common.api.enums.identity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 自然人账号对外引用类型（管理端路径、邀请体等）。库内仍以 {@code sec_user_account.id} 关联。
 *
 * <p>路径前缀见 {@link com.aaron.cloud.common.api.identity.AccountPrincipalPaths}。
 */
@Getter
@RequiredArgsConstructor
public enum AccountPrincipalKind {
    /** 推荐：{@code sec_user_account.account_no} 对外账号编号。 */
    ACCOUNT_NO("ac"),
    /** 密码登录凭证 {@code login_name}。 */
    LOGIN_NAME("ln"),
    /** 绑定邮箱 {@code email}。 */
    EMAIL("em"),
    /** 绑定手机号 {@code phone}。 */
    PHONE("ph");

    private final String pathPrefix;

    public static AccountPrincipalKind fromPathPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return LOGIN_NAME;
        }
        String p = prefix.trim().toLowerCase();
        for (AccountPrincipalKind k : values()) {
            if (k.pathPrefix.equals(p)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown account principal kind: " + prefix);
    }
}
