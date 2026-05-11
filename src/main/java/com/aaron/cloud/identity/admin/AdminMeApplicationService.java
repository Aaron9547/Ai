package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMeApplicationService {

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final SysTenantRepository tenantRepository;
    private final AdminMenuAuthorizationService adminMenuAuthorizationService;

    public AdminMeView snapshot() {
        var snap = TenantContextHolder.require();
        Long uid = snap.getUserId();
        String loginName = resolveLoginName(uid);
        String displayName = resolveDisplayName(uid);
        List<MembershipRow> memberships = List.of();
        if (uid != null) {
            memberships =
                    tenantMemberRepository.listByUserId(uid).stream()
                            .filter(m -> m.getStatus() == UserAccountStatus.ACTIVE)
                            .map(
                                    m -> {
                                        var tenantOpt = tenantRepository.findById(m.getTenantId());
                                        return new MembershipRow(
                                                m.getTenantId(),
                                                tenantOpt.map(t -> t.getCode()).orElse(""),
                                                tenantOpt
                                                        .map(t -> t.getName())
                                                        .filter(n -> n != null && !n.isBlank())
                                                        .orElse(tenantOpt.map(t -> t.getCode()).orElse("")),
                                                m.getRoleCode());
                                    })
                            .toList();
        }
        TenantMemberRole role = snap.getMemberRole();
        boolean adminPortal = role != null && role != TenantMemberRole.MEMBER;
        return new AdminMeView(
                loginName,
                displayName,
                snap.getTenantId(),
                role,
                memberships,
                List.copyOf(adminMenuAuthorizationService.allowedMenuCodesForSnapshot()),
                adminPortal);
    }

    private String resolveLoginName(Long uid) {
        if (uid != null) {
            return userAccountRepository
                    .findById(uid)
                    .map(SecUserAccount::getLoginName)
                    .orElse("");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return auth != null ? auth.getName() : "";
    }

    /** 管理端顶栏展示用；库中无或非空白的显示名时返回空串，由前端回退到 {@link #loginName}。 */
    private String resolveDisplayName(Long uid) {
        if (uid == null) {
            return "";
        }
        return userAccountRepository
                .findById(uid)
                .map(SecUserAccount::getDisplayName)
                .filter(s -> s != null && !s.isBlank())
                .orElse("");
    }

    public record AdminMeView(
            String loginName,
            String displayName,
            Long tenantId,
            TenantMemberRole memberRole,
            List<MembershipRow> memberships,
            List<String> allowedMenuCodes,
            boolean adminPortal) {}

    public record MembershipRow(long tenantId, String tenantCode, String tenantName, TenantMemberRole role) {}
}
