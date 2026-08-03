package com.aaron.cloud.identity.service;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
import com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.UserAccountProfileSupport;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.identity.account.UserAccountProvisioningService;
import com.aaron.cloud.identity.jwt.JwtLocalAdminTokenService;
import com.aaron.cloud.identity.rest.open.AuthLoginController.LoginResponse;
import com.aaron.cloud.identity.rest.open.AuthLoginController.RegisterRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** C 端自助注册：账号档案落库、租户成员、设备会话/画像归并。 */
@Service
@RequiredArgsConstructor
public class OpenRegistrationApplicationService {

    private final UserAccountProvisioningService userAccountProvisioningService;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtLocalAdminTokenService jwtLocalAdminTokenService;
    private final ProfileDeviceMergeApplicationService profileDeviceMergeApplicationService;

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(long tenantId, RegisterRequest req, String deviceIdHeaderOrNull)
            throws Exception {
        String loginName = resolveLoginName(req);
        String email = resolveEmail(req, loginName);
        String phone = resolvePhone(req);
        var u = new SecUserAccount();
        u.setLoginName(loginName);
        u.setEmail(email);
        u.setPhone(phone);
        u.setDisplayName(
                req.getDisplayName() == null || req.getDisplayName().isBlank()
                        ? defaultDisplayName(loginName, email)
                        : req.getDisplayName().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword() == null ? "" : req.getPassword()));
        u.setStatus(UserAccountStatus.ACTIVE);
        u.setRegistrationChannel(
                UserAccountProfileSupport.inferChannel(loginName, email, phone, false));
        userAccountProvisioningService.insert(u);

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

    private static String defaultDisplayName(String loginName, String email) {
        String base = email != null ? email : loginName;
        int at = base.indexOf('@');
        if (at > 0) {
            return base.substring(0, at);
        }
        return base;
    }

    private static String resolveLoginName(RegisterRequest req) {
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            return UserAccountProfileSupport.normalizeEmail(req.getEmail());
        }
        if (req.getLoginName() == null) {
            return "";
        }
        return req.getLoginName().trim();
    }

    private static String resolveEmail(RegisterRequest req, String loginName) {
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            return UserAccountProfileSupport.normalizeEmail(req.getEmail());
        }
        if (loginName != null && loginName.contains("@")) {
            return UserAccountProfileSupport.normalizeEmail(loginName);
        }
        return null;
    }

    private static String resolvePhone(RegisterRequest req) {
        if (req.getPhone() == null || req.getPhone().isBlank()) {
            return null;
        }
        return UserAccountProfileSupport.normalizePhone(req.getPhone());
    }
}
