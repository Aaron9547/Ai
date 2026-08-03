package com.aaron.cloud.identity.account;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.identity.AccountPrincipalRef;
import com.aaron.cloud.common.context.LoginUser;
import com.aaron.cloud.common.context.LoginUserContextHolder;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 管理端禁止对当前登录自然人执行的操作（以登录名/账号主体比对，不依赖 JWT {@code uid}）。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountSelfOperationGuard {

    public static void assertNotSelf(SecUserAccount target) {
        if (target == null || target.getLoginName() == null) {
            return;
        }
        LoginUser caller = LoginUserContextHolder.getOrNull();
        if (caller == null || caller.getLoginName() == null) {
            return;
        }
        if (caller.getLoginName().equalsIgnoreCase(target.getLoginName().trim())) {
            throw new IllegalArgumentException(ErrorCodes.EX_MSG_CANNOT_OPERATE_ON_SELF);
        }
    }

    public static void assertNotSelf(AccountPrincipalRef targetPrincipal, String targetLoginName) {
        LoginUser caller = LoginUserContextHolder.getOrNull();
        if (caller == null || caller.getLoginName() == null) {
            return;
        }
        String callerLogin = caller.getLoginName().trim();
        if (targetLoginName != null && callerLogin.equalsIgnoreCase(targetLoginName.trim())) {
            throw new IllegalArgumentException(ErrorCodes.EX_MSG_CANNOT_OPERATE_ON_SELF);
        }
        if (targetPrincipal != null
                && targetPrincipal.loginNameOrNull() != null
                && callerLogin.equalsIgnoreCase(targetPrincipal.loginNameOrNull())) {
            throw new IllegalArgumentException(ErrorCodes.EX_MSG_CANNOT_OPERATE_ON_SELF);
        }
    }
}
