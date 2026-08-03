package com.aaron.cloud.identity.rest.open;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.web.HttpClientIp;
import com.aaron.cloud.common.web.LoginRegionResolver;
import com.aaron.cloud.common.web.ApiErrorResponse;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import com.aaron.cloud.identity.jwt.JwtLocalAdminTokenService;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import com.aaron.cloud.identity.open.OpenRegistrationEmailSupport;
import com.aaron.cloud.identity.open.OpenRegistrationEmailVerificationService;
import com.aaron.cloud.identity.open.OpenRegistrationVerificationSender;
import com.aaron.cloud.identity.service.OpenRegistrationApplicationService;
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
    private final OpenRegistrationApplicationService openRegistrationApplicationService;
    private final OpenRegistrationEmailVerificationService emailVerificationService;
    private final OpenRegistrationVerificationSender registrationVerificationSender;
    private final MessageSceneReadinessQuery messageSceneReadinessQuery;
    private final ProfileDeviceMergeApplicationService profileDeviceMergeApplicationService;

    @Value("${ai.tenant.default-id:1}")
    private long defaultTenantId;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) throws Exception {
        String loginName = normalizeLoginIdentifier(req);
        if (loginName.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        var userOpt = userAccountRepository.findByLoginIdentifier(loginName);
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
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId != null && !deviceId.isBlank()) {
            for (SysTenantMember m : active) {
                try {
                    profileDeviceMergeApplicationService.mergeGuestDeviceToUser(
                            m.getTenantId(), user.getId(), deviceId);
                } catch (Exception ex) {
                    log.warn(
                            "login guest device merge failed tenantId={} userId={}",
                            m.getTenantId(),
                            user.getId(),
                            ex);
                }
            }
        }
        try {
            recordLastLogin(user.getId(), request);
        } catch (Exception ex) {
            log.warn("update last_login failed userId={}", user.getId(), ex);
        }
        return ResponseEntity.ok(jwtLocalAdminTokenService.buildLoginResponse(user, active, headerTid));
    }

    /**
     * C 端自助注册：在指定租户下创建 {@link TenantMemberRole#MEMBER} 账号并签发与登录相同的 JWT。
     */
    /** 发送注册邮箱验证码（须租户开放注册）。 */
    @PostMapping("/register/send-code")
    public ResponseEntity<?> sendRegisterCode(
            @RequestBody SendRegisterCodeRequest req, HttpServletRequest request) {
        String email = OpenRegistrationEmailSupport.normalize(req.getEmail());
        if (!OpenRegistrationEmailSupport.isValid(email)) {
            return badRegisterError(HttpStatus.BAD_REQUEST, ErrorCodes.REGISTER_EMAIL_INVALID, "invalid email");
        }
        long tenantId = resolveSignupTenantId(request);
        if (tenantRepository.findById(tenantId).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!isOpenRegistrationAvailable(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (userAccountRepository.existsEmail(email, null)
                || userAccountRepository.existsLoginName(email, null)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_NAME_CONFLICT)
                                    .message("email already registered")
                                    .build());
        }
        long cooldown = emailVerificationService.remainingCooldownSeconds(tenantId, email);
        if (cooldown > 0) {
            return ResponseEntity.ok(new SendRegisterCodeResponse(false, cooldown));
        }
        String code = emailVerificationService.issueCode(tenantId, email);
        String tenantName =
                tenantRepository.findById(tenantId).map(t -> t.getName()).orElse("");
        registrationVerificationSender.sendRegisterCode(
                tenantId, tenantName, email, code, emailVerificationService.getCodeTtlMinutes(tenantId));
        return ResponseEntity.ok(
                new SendRegisterCodeResponse(true, emailVerificationService.getSendCooldownSeconds(tenantId)));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletRequest request)
            throws Exception {
        String email = OpenRegistrationEmailSupport.normalize(req.getEmail());
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (!OpenRegistrationEmailSupport.isValid(email)) {
            return badRegisterError(HttpStatus.BAD_REQUEST, ErrorCodes.REGISTER_EMAIL_INVALID, "invalid email");
        }
        if (!OpenRegistrationEmailSupport.isStrongEnoughPassword(password)) {
            return badRegisterError(HttpStatus.BAD_REQUEST, ErrorCodes.REGISTER_PASSWORD_WEAK, "weak password");
        }
        long tenantId = resolveSignupTenantId(request);
        if (tenantRepository.findById(tenantId).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!isOpenRegistrationAvailable(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (userAccountRepository.existsEmail(email, null)
                || userAccountRepository.existsLoginName(email, null)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_NAME_CONFLICT)
                                    .message("email already registered")
                                    .build());
        }
        if (!emailVerificationService.verifyAndConsume(tenantId, email, req.getVerificationCode())) {
            HttpStatus status =
                    emailVerificationService.hasPendingCode(tenantId, email)
                            ? HttpStatus.BAD_REQUEST
                            : HttpStatus.GONE;
            return badRegisterError(status, ErrorCodes.REGISTER_CODE_INVALID, "invalid verification code");
        }

        req.setEmail(email);
        String deviceId = request.getHeader("X-Device-Id");
        LoginResponse body = openRegistrationApplicationService.register(tenantId, req, deviceId);
        userAccountRepository.findByLoginIdentifier(email).ifPresent(u -> {
            try {
                recordLastLogin(u.getId(), request);
            } catch (Exception ex) {
                log.warn("update last_login failed userId={}", u.getId(), ex);
            }
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private static ResponseEntity<ApiErrorResponse> badRegisterError(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.builder().code(code).message(message).build());
    }

    private static String normalizeLoginIdentifier(LoginRequest req) {
        String raw = req.getLoginName() == null ? "" : req.getLoginName().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.contains("@")) {
            return com.aaron.cloud.common.security.UserAccountProfileSupport.normalizeEmail(raw);
        }
        String phone = com.aaron.cloud.common.security.UserAccountProfileSupport.normalizePhone(raw);
        if (phone != null && com.aaron.cloud.common.security.UserAccountProfileSupport.isValidCnPhone(phone)) {
            return phone;
        }
        return raw;
    }

    private static String trimLoginName(LoginRequest req) {
        if (req.getLoginName() == null) {
            return "";
        }
        return req.getLoginName().trim();
    }

    private boolean isOpenRegistrationAvailable(long tenantId) {
        if (!tenantRuntimeSettingApplicationService.isAuthOpenRegistrationEnabled(tenantId)) {
            return false;
        }
        return messageSceneReadinessQuery.isSceneConfigured(
                tenantId, MessageSceneCode.REGISTER_VERIFICATION);
    }

    /** 登录/注册成功后写入 {@code sec_user_account.last_login_*}（管理端成员列表与数据概览埋点）。 */
    private void recordLastLogin(long userId, HttpServletRequest request) {
        String ip = HttpClientIp.resolve(request);
        String region = LoginRegionResolver.resolve(request);
        userAccountRepository.updateLastLogin(userId, BeijingTime.nowLocal(), ip, region);
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
    public static class SendRegisterCodeRequest {
        private String email;
    }

    public record SendRegisterCodeResponse(boolean sent, long cooldownSeconds) {}

    @Data
    public static class RegisterRequest {
        /** 注册邮箱（写入 {@code email}，且默认作为 {@code login_name}）。 */
        private String email;
        /** 自定义登录名注册（与 {@link #email} 二选一或互补）。 */
        private String loginName;
        /** 手机号注册（预留，须配套短信验证）。 */
        private String phone;
        private String password;
        private String displayName;
        private String verificationCode;
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            List<MembershipEntry> memberships) {

        public record MembershipEntry(long tenantId, String tenantCode, TenantMemberRole role, String tenantName) {}
    }
}
