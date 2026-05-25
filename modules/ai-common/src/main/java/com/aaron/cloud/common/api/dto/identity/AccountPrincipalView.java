package com.aaron.cloud.common.api.dto.identity;

import com.aaron.cloud.common.api.identity.AccountPrincipalRef;

/** API 响应中的账号主体（不含库内雪花 id）。 */
public record AccountPrincipalView(String kind, String value) {

    public static AccountPrincipalView from(AccountPrincipalRef ref) {
        return new AccountPrincipalView(ref.kind().name(), ref.value());
    }

    public static AccountPrincipalView fromLoginName(String loginName) {
        return from(AccountPrincipalRef.loginName(loginName));
    }

    public static AccountPrincipalView fromAccountNo(String accountNo) {
        return from(AccountPrincipalRef.accountNo(accountNo));
    }
}
