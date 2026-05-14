package com.aaron.cloud.identity.service;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.identity.jwt.JwtLocalAdminTokenService;
import com.aaron.cloud.identity.rest.open.AuthLoginController.LoginResponse;
import com.aaron.cloud.identity.rest.open.AuthLoginController.RegisterRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** C 端自助注册事务边界：账号落库、租户成员、设备会话/画像归并。 */
@Service
@RequiredArgsConstructor
public class OpenRegistrationApplicationService {

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtLocalAdminTokenService jwtLocalAdminTokenService;
    private final ProfileDeviceMergeApplicationService profileDeviceMergeApplicationService;

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(long tenantId, RegisterRequest req, String deviceIdHeaderOrNull)
            throws Exception {
        var u = new SecUserAccount();
        String loginName = trimLoginName(req);
        u.setLoginName(loginName);
        u.setDisplayName(
                req.getDisplayName() == null || req.getDisplayName().isBlank()
                        ? loginName
                        : req.getDisplayName().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword() == null ? "" : req.getPassword()));
        u.setStatus(UserAccountStatus.ACTIVE);
        userAccountRepository.insert(u);

        var m = new SysTenantMember();
        m.setTenantId(tenantId);
        m.setUserId(u.getId());
        m.setRoleCode(TenantMemberRole.MEMBER);
        m.setStatus(UserAccountStatus.ACTIVE);
        tenantMemberRepository.insert(m);

        profileDeviceMergeApplicationService.mergeGuestDeviceToUser(tenantId, u.getId(), deviceIdHeaderOrNull);

        List<SysTenantMember> active = List.of(m);
        return jwtLocalAdminTokenService.buildLoginResponse(u, active, tenantId);
    }

    private static String trimLoginName(RegisterRequest req) {
        if (req.getLoginName() == null) {
            return "";
        }
        return req.getLoginName().trim();
    }
}
