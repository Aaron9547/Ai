package com.aaron.cloud.identity.rest.open;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.web.ApiErrorResponse;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import com.aaron.cloud.identity.jwt.JwtLocalAdminTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.providers.auth", havingValue = "jwt-local")
public class AuthLoginController extends OpenV1ControllerBases.Auth {

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final SysTenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtLocalAdminTokenService jwtLocalAdminTokenService;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @Value("${ai.tenant.default-id:1}")
    private long defaultTenantId;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) throws Exception {
        String loginName = trimLoginName(req);
        if (loginName.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        var userOpt = userAccountRepository.findByLoginName(loginName);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        SecUserAccount user = userOpt.get();
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            log.warn(
                    "login forbidden: loginName={} userId={} account status not ACTIVE",
                    user.getLoginName(),
                    user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_ACCOUNT_DISABLED)
                                    .message("account not active")
                                    .build());
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).build();
        }
        List<SysTenantMember> memberships = tenantMemberRepository.listByUserId(user.getId());
        List<SysTenantMember> active =
                memberships.stream().filter(m -> m.getStatus() == UserAccountStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            for (SysTenantMember m : memberships) {
                log.warn(
                        "login tenant_member row id={} tenant_id={} user_id={} status={} role={}",
                        m.getId(),
                        m.getTenantId(),
                        m.getUserId(),
                        m.getStatus(),
                        m.getRoleCode());
            }
            log.warn(
                    "login forbidden: loginName={} userId={} tenant_member rows={} but none ACTIVE — 检查 sys_tenant_member.status 是否为 1、user_id 是否与 sec_user_account.id 一致；若 status 已为 1 仍失败，检查 ORM 枚举与库列类型是否一致",
                    user.getLoginName(),
                    user.getId(),
                    memberships.size());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_NO_ACTIVE_MEMBERSHIP)
                                    .message("no active tenant membership")
                                    .build());
        }
        Long headerTid = parseLongHeaderNullable(request, "X-Tenant-Id");
        return ResponseEntity.ok(jwtLocalAdminTokenService.buildLoginResponse(user, active, headerTid));
    }

    /**
     * C 端自助注册：在指定租户下创建 {@link TenantMemberRole#MEMBER} 账号并签发与登录相同的 JWT。
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest req, HttpServletRequest request)
            throws Exception {
        String loginName = trimLoginName(req);
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (loginName.length() < 3 || loginName.length() > 64) {
            return ResponseEntity.badRequest().build();
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().build();
        }
        if (userAccountRepository.existsLoginName(loginName, null)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        long tenantId = resolveSignupTenantId(request);
        if (tenantRepository.findById(tenantId).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!tenantRuntimeSettingApplicationService.isAuthOpenRegistrationEnabled(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var u = new SecUserAccount();
        u.setLoginName(loginName);
        u.setDisplayName(
                req.getDisplayName() == null || req.getDisplayName().isBlank()
                        ? loginName
                        : req.getDisplayName().trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(UserAccountStatus.ACTIVE);
        userAccountRepository.insert(u);

        var m = new SysTenantMember();
        m.setTenantId(tenantId);
        m.setUserId(u.getId());
        m.setRoleCode(TenantMemberRole.MEMBER);
        m.setStatus(UserAccountStatus.ACTIVE);
        tenantMemberRepository.insert(m);

        List<SysTenantMember> active = List.of(m);
        return ResponseEntity.status(HttpStatus.CREATED).body(jwtLocalAdminTokenService.buildLoginResponse(u, active, tenantId));
    }

    private static String trimLoginName(LoginRequest req) {
        if (req.getLoginName() == null) {
            return "";
        }
        return req.getLoginName().trim();
    }

    private static String trimLoginName(RegisterRequest req) {
        if (req.getLoginName() == null) {
            return "";
        }
        return req.getLoginName().trim();
    }

    private long resolveSignupTenantId(HttpServletRequest request) {
        Long h = parseLongHeaderNullable(request, "X-Tenant-Id");
        if (h != null && tenantRepository.findById(h).isPresent()) {
            return h;
        }
        return defaultTenantId;
    }

    private static Long parseLongHeaderNullable(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Data
    public static class LoginRequest {
        private String loginName;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        private String loginName;
        private String password;
        private String displayName;
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            List<MembershipEntry> memberships) {

        public record MembershipEntry(long tenantId, String tenantCode, TenantMemberRole role, String tenantName) {}
    }
}
