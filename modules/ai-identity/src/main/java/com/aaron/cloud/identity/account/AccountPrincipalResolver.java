package com.aaron.cloud.identity.account;

import com.aaron.cloud.common.api.enums.identity.AccountPrincipalKind;
import com.aaron.cloud.common.api.identity.AccountPrincipalRef;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.UserAccountProfileSupport;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 将 {@link AccountPrincipalRef} 解析为 {@link SecUserAccount}（内部仍用雪花 id 落库）。 */
@Component
@RequiredArgsConstructor
public class AccountPrincipalResolver {

    private final SecUserAccountRepository userAccountRepository;

    public SecUserAccount requireAccount(AccountPrincipalRef principal) {
        return switch (principal.kind()) {
            case ACCOUNT_NO ->
                    userAccountRepository
                            .findByAccountNo(principal.value())
                            .orElseThrow(() -> new IllegalArgumentException("未找到该账号编号的用户"));
            case LOGIN_NAME ->
                    userAccountRepository
                            .findByLoginName(principal.value())
                            .orElseThrow(() -> new IllegalArgumentException("未找到该登录名的用户"));
            case EMAIL -> {
                String em = UserAccountProfileSupport.normalizeEmail(principal.value());
                yield userAccountRepository
                        .findByEmail(em)
                        .orElseThrow(() -> new IllegalArgumentException("未找到该邮箱绑定的用户"));
            }
            case PHONE -> {
                String ph = UserAccountProfileSupport.normalizePhone(principal.value());
                yield userAccountRepository
                        .findByPhone(ph)
                        .orElseThrow(() -> new IllegalArgumentException("未找到该手机号绑定的用户"));
            }
        };
    }

    public SecUserAccount requireActiveAccount(AccountPrincipalRef principal) {
        SecUserAccount u = requireAccount(principal);
        if (u.getStatus() != UserAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("user account not active");
        }
        return u;
    }
}
