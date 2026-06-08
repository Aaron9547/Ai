package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 知识星球周报邮件收件人资格：账号与租户成员均须 {@link UserAccountStatus#ACTIVE}。 */
@Component
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyRecipientGate {

    public static final String SKIP_USER_NOT_FOUND = "用户不存在";
    public static final String SKIP_ACCOUNT_DISABLED = "账号已禁用";
    public static final String SKIP_NOT_TENANT_MEMBER = "非租户成员";
    public static final String SKIP_MEMBER_DISABLED = "成员已禁用";

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;

    public boolean isEligible(long tenantId, long userId) {
        return skipReason(tenantId, userId).isEmpty();
    }

    public Optional<String> skipReason(long tenantId, long userId) {
        SecUserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return Optional.of(SKIP_USER_NOT_FOUND);
        }
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            return Optional.of(SKIP_ACCOUNT_DISABLED);
        }
        SysTenantMember member = tenantMemberRepository.find(tenantId, userId).orElse(null);
        if (member == null) {
            return Optional.of(SKIP_NOT_TENANT_MEMBER);
        }
        if (member.getStatus() != UserAccountStatus.ACTIVE) {
            return Optional.of(SKIP_MEMBER_DISABLED);
        }
        return Optional.empty();
    }
}
