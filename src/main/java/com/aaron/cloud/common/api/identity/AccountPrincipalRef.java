package com.aaron.cloud.common.api.identity;

import com.aaron.cloud.common.api.enums.identity.AccountPrincipalKind;

/** 管理端/API 层账号主体：类型 + 规范化后的值（不含路径前缀）。 */
public record AccountPrincipalRef(AccountPrincipalKind kind, String value) {

    public AccountPrincipalRef {
        if (kind == null) {
            kind = AccountPrincipalKind.LOGIN_NAME;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("account principal value required");
        }
        value = value.trim();
    }

    public static AccountPrincipalRef loginName(String loginName) {
        return new AccountPrincipalRef(AccountPrincipalKind.LOGIN_NAME, loginName);
    }

    public static AccountPrincipalRef accountNo(String accountNo) {
        return new AccountPrincipalRef(AccountPrincipalKind.ACCOUNT_NO, accountNo);
    }

    public static AccountPrincipalRef email(String email) {
        return new AccountPrincipalRef(AccountPrincipalKind.EMAIL, email);
    }

    public static AccountPrincipalRef phone(String phone) {
        return new AccountPrincipalRef(AccountPrincipalKind.PHONE, phone);
    }

    public String loginNameOrNull() {
        return kind == AccountPrincipalKind.LOGIN_NAME ? value : null;
    }
}
