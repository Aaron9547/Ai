package com.aaron.cloud.common.context;

import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import lombok.Builder;
import lombok.Value;

/** 当前请求登录用户快照（不含 {@code passwordHash}）；由网关 Filter 写入 {@link LoginUserContextHolder}。 */
@Value
@Builder
public class LoginUser {

    long id;
    String loginName;
    String displayName;
    UserAccountStatus status;
    /** 与 {@code sec_user_account.jwt_seq} 一致；签发 JWT 时使用。 */
    Long jwtSeq;

    public static LoginUser fromAccount(SecUserAccount account) {
        if (account == null || account.getId() == null) {
            return null;
        }
        return LoginUser.builder()
                .id(account.getId())
                .loginName(account.getLoginName())
                .displayName(account.getDisplayName())
                .status(account.getStatus())
                .jwtSeq(account.getJwtSeq())
                .build();
    }

    public long jwtSeqOrZero() {
        return jwtSeq == null ? 0L : jwtSeq;
    }

    /** 顶栏/列表展示：优先昵称，否则登录名。 */
    public String displayLabel() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return loginName == null ? "" : loginName.trim();
    }
}
