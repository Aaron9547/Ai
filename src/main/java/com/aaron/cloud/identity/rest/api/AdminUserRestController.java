package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.dto.identity.AccountPrincipalView;
import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
import com.aaron.cloud.common.security.UserAccountProfileSupport;
import com.aaron.cloud.common.api.identity.AccountPrincipalPaths;
import com.aaron.cloud.common.api.identity.AccountPrincipalRef;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.account.AccountPrincipalResolver;
import com.aaron.cloud.identity.account.AccountSelfOperationGuard;
import com.aaron.cloud.identity.account.UserAccountProvisioningService;
import com.aaron.cloud.identity.admin.UserAccessPresenceService;
import com.aaron.cloud.identity.admin.UserAdminMenuApplicationService;
import com.aaron.cloud.identity.tenant.TenantMemberRoleApplicationService;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端本租户账号：路径以 {@link AccountPrincipalRef} 定位用户（{@code ln:登录名}），不使用雪花 id 暴露给前端。
 */
@RestController
@RequiredArgsConstructor
public class AdminUserRestController extends ApiV1ControllerBases.AdminUsers {

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAdminMenuApplicationService userAdminMenuApplicationService;
    private final TenantMemberRoleApplicationService tenantMemberRoleApplicationService;
    private final UserAccessPresenceService userAccessPresenceService;
    private final AccountPrincipalResolver accountPrincipalResolver;
    private final UserAccountProvisioningService userAccountProvisioningService;

    @GetMapping
    public List<UserView> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long tenantId) {
        var snap = TenantContextHolder.require();
        long tenantIdForPage = snap.getTenantId();
        if (tenantId != null) {
            if (snap.getMemberRole() == null || !snap.getMemberRole().isFounder()) {
                throw new AccessDeniedException("仅创始人可指定 tenantId");
            }
            tenantIdForPage = tenantId;
        }
        var records = tenantMemberRepository.pageByTenant(tenantIdForPage, page, size).getRecords();
        List<SecUserAccount> accounts = new ArrayList<>();
        List<TenantMemberRole> roles = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        for (SysTenantMember m : records) {
            var u = userAccountRepository.findById(m.getUserId()).orElse(null);
            if (u == null) {
                continue;
            }
            TenantMemberRole tr = m.getStatus() == UserAccountStatus.ACTIVE ? m.getRoleCode() : null;
            accounts.add(u);
            roles.add(tr);
            userIds.add(u.getId());
        }
        var online = userAccessPresenceService.onlineAmong(tenantIdForPage, userIds);
        List<UserView> views = new ArrayList<>(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            SecUserAccount u = accounts.get(i);
            views.add(toUserView(u, roles.get(i), online.contains(u.getId())));
        }
        return views;
    }

    @GetMapping("/{account}/admin-menus")
    public List<String> listUserAdminMenus(@PathVariable String account) {
        SecUserAccount u = resolveInCurrentTenant(account);
        return userAdminMenuApplicationService.listForUser(u.getId());
    }

    @PutMapping("/{account}/admin-menus")
    public void replaceUserAdminMenus(
            @PathVariable String account, @RequestBody ReplaceUserAdminMenusBody body) {
        SecUserAccount u = resolveInCurrentTenant(account);
        userAdminMenuApplicationService.replaceForUser(u.getId(), body.getMenuCodes());
    }

    @PutMapping("/{account}/tenant-role")
    public UserView updateTenantRole(@PathVariable String account, @RequestBody UpdateTenantRoleBody body) {
        AccountPrincipalRef principal = AccountPrincipalPaths.parsePathSegment(account);
        var snap = TenantContextHolder.require();
        if (body.getTenantId() != null
                && (snap.getMemberRole() == null || !snap.getMemberRole().isFounder())) {
            throw new AccessDeniedException("仅创始人可指定 tenantId");
        }
        if (body.getTenantId() == null) {
            resolveInCurrentTenant(account);
        } else {
            SecUserAccount u = accountPrincipalResolver.requireAccount(principal);
            tenantMemberRepository
                    .find(body.getTenantId(), u.getId())
                    .orElseThrow(() -> new AccessDeniedException("用户不在目标租户"));
        }
        tenantMemberRoleApplicationService.updateMemberRole(principal, body.getRole(), body.getTenantId());
        SecUserAccount u = accountPrincipalResolver.requireAccount(principal);
        long tidForRead =
                body.getTenantId() != null ? body.getTenantId() : TenantContextHolder.require().getTenantId();
        TenantMemberRole tenantRole =
                tenantMemberRepository.find(tidForRead, u.getId()).map(SysTenantMember::getRoleCode).orElse(null);
        return toUserView(u, tenantRole, tidForRead);
    }

    @GetMapping("/{account}")
    public UserView get(@PathVariable String account) {
        SecUserAccount u = resolveInCurrentTenant(account);
        var snap = TenantContextHolder.require();
        SysTenantMember membership =
                tenantMemberRepository
                        .find(snap.getTenantId(), u.getId())
                        .orElseThrow(() -> new AccessDeniedException("用户不在当前租户"));
        TenantMemberRole tenantRole =
                membership.getStatus() == UserAccountStatus.ACTIVE ? membership.getRoleCode() : null;
        return toUserView(u, tenantRole, snap.getTenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestBody CreateUserBody body) {
        var snap = TenantContextHolder.require();
        if (body.getRole() == TenantMemberRole.FOUNDER) {
            var caller = TenantContextHolder.require();
            if (caller.getMemberRole() == null || !caller.getMemberRole().isFounder()) {
                throw new IllegalArgumentException("仅创始人可分配创始人角色");
            }
        }
        String loginName = body.getLoginName().trim();
        if (userAccountRepository.existsLoginName(loginName, null)) {
            throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
        }
        String email =
                body.getEmail() == null || body.getEmail().isBlank()
                        ? null
                        : UserAccountProfileSupport.normalizeEmail(body.getEmail());
        String phone =
                body.getPhone() == null || body.getPhone().isBlank()
                        ? null
                        : UserAccountProfileSupport.normalizePhone(body.getPhone());
        if (email != null && userAccountRepository.existsEmail(email, null)) {
            throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
        }
        if (phone != null && userAccountRepository.existsPhone(phone, null)) {
            throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
        }
        var u = new SecUserAccount();
        u.setLoginName(loginName);
        u.setEmail(email);
        u.setPhone(phone);
        u.setDisplayName(body.getDisplayName() == null ? "" : body.getDisplayName());
        u.setPasswordHash(passwordEncoder.encode(body.getPassword()));
        u.setStatus(UserAccountStatus.ACTIVE);
        u.setRegistrationChannel(
                UserAccountProfileSupport.inferChannel(loginName, email, phone, true));
        userAccountProvisioningService.insert(u);
        var m = new SysTenantMember();
        m.setTenantId(snap.getTenantId());
        m.setUserId(u.getId());
        m.setRoleCode(body.getRole() == null ? TenantMemberRole.MEMBER : body.getRole());
        m.setStatus(UserAccountStatus.ACTIVE);
        tenantMemberRepository.insert(m);
        TenantMemberRole role = body.getRole() == null ? TenantMemberRole.MEMBER : body.getRole();
        return toUserView(u, role, false);
    }

    @PutMapping("/{account}")
    public UserView update(@PathVariable String account, @RequestBody UpdateUserBody body) {
        SecUserAccount u = resolveInCurrentTenant(account);
        if (body.getStatus() != null) {
            AccountSelfOperationGuard.assertNotSelf(u);
        }
        if (body.getDisplayName() != null) {
            u.setDisplayName(body.getDisplayName());
        }
        if (body.getPassword() != null && !body.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(body.getPassword()));
        }
        if (body.getStatus() != null) {
            u.setStatus(body.getStatus());
        }
        if (body.getLoginName() != null && !body.getLoginName().isBlank()) {
            String nextLogin = body.getLoginName().trim();
            if (userAccountRepository.existsLoginName(nextLogin, u.getId())) {
                throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
            }
            u.setLoginName(nextLogin);
        }
        if (body.getEmail() != null) {
            String nextEmail =
                    body.getEmail().isBlank()
                            ? null
                            : UserAccountProfileSupport.normalizeEmail(body.getEmail());
            if (nextEmail != null && userAccountRepository.existsEmail(nextEmail, u.getId())) {
                throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
            }
            u.setEmail(nextEmail);
        }
        if (body.getPhone() != null) {
            String nextPhone =
                    body.getPhone().isBlank()
                            ? null
                            : UserAccountProfileSupport.normalizePhone(body.getPhone());
            if (nextPhone != null && userAccountRepository.existsPhone(nextPhone, u.getId())) {
                throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
            }
            u.setPhone(nextPhone);
        }
        userAccountRepository.updateById(u);
        TenantMemberRole tenantRole =
                tenantMemberRepository
                        .find(TenantContextHolder.require().getTenantId(), u.getId())
                        .map(
                                m ->
                                        m.getStatus() == UserAccountStatus.ACTIVE
                                                ? m.getRoleCode()
                                                : null)
                        .orElse(null);
        return toUserView(u, tenantRole, TenantContextHolder.require().getTenantId());
    }

    @PostMapping("/{account}/kick-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kickSession(@PathVariable String account) {
        SecUserAccount u = resolveManagedInCurrentTenant(account);
        userAccountRepository.incrementJwtSeq(u.getId());
    }

    @PostMapping("/{account}/ban")
    public UserView ban(@PathVariable String account) {
        SecUserAccount u = resolveManagedInCurrentTenant(account);
        var snap = TenantContextHolder.require();
        u.setStatus(UserAccountStatus.DISABLED);
        userAccountRepository.updateById(u);
        tenantMemberRepository
                .find(snap.getTenantId(), u.getId())
                .ifPresent(m -> tenantMemberRepository.updateById(deletedMember(m)));
        userAccountRepository.incrementJwtSeq(u.getId());
        return get(account);
    }

    @DeleteMapping("/{account}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String account) {
        SecUserAccount u = resolveInCurrentTenant(account);
        var snap = TenantContextHolder.require();
        tenantMemberRepository
                .find(snap.getTenantId(), u.getId())
                .ifPresent(m -> tenantMemberRepository.updateById(deletedMember(m)));
        u.setStatus(UserAccountStatus.DISABLED);
        userAccountRepository.updateById(u);
    }

    private SecUserAccount resolveInCurrentTenant(String accountPath) {
        AccountPrincipalRef principal = AccountPrincipalPaths.parsePathSegment(accountPath);
        SecUserAccount u = accountPrincipalResolver.requireAccount(principal);
        var snap = TenantContextHolder.require();
        tenantMemberRepository
                .find(snap.getTenantId(), u.getId())
                .orElseThrow(() -> new AccessDeniedException("用户不在当前租户"));
        return u;
    }

    private SecUserAccount resolveManagedInCurrentTenant(String accountPath) {
        SecUserAccount u = resolveInCurrentTenant(accountPath);
        AccountSelfOperationGuard.assertNotSelf(u);
        return u;
    }

    private static SysTenantMember deletedMember(SysTenantMember m) {
        m.setStatus(UserAccountStatus.DISABLED);
        return m;
    }

    private UserView toUserView(SecUserAccount u, TenantMemberRole tenantRole, long tenantIdForPresence) {
        boolean online =
                userAccessPresenceService.onlineAmong(tenantIdForPresence, List.of(u.getId())).contains(u.getId());
        return toUserView(u, tenantRole, online);
    }

    private UserView toUserView(SecUserAccount u, TenantMemberRole tenantRole, boolean sessionOnline) {
        String lastLoginAt = BeijingTime.formatDisplay(u.getLastLoginAt());
        String registeredAt = BeijingTime.formatDisplay(u.getRegisteredAt());
        return new UserView(
                AccountPrincipalView.fromAccountNo(u.getAccountNo()),
                u.getAccountNo(),
                u.getLoginName(),
                u.getEmail(),
                u.getPhone(),
                u.getRegistrationChannel(),
                registeredAt,
                u.getDisplayName(),
                u.getStatus(),
                tenantRole,
                sessionOnline,
                lastLoginAt,
                u.getLastLoginIp(),
                u.getLastLoginRegion());
    }

    public record UserView(
            AccountPrincipalView account,
            String accountNo,
            String loginName,
            String email,
            String phone,
            UserRegistrationChannel registrationChannel,
            String registeredAt,
            String displayName,
            UserAccountStatus status,
            TenantMemberRole tenantRole,
            boolean sessionOnline,
            String lastLoginAt,
            String lastLoginIp,
            String lastLoginRegion) {}

    @Data
    public static class ReplaceUserAdminMenusBody {
        private List<String> menuCodes;
    }

    @Data
    public static class CreateUserBody {
        @NotBlank private String loginName;

        private String email;
        private String phone;
        private String displayName;
        @NotBlank private String password;
        private TenantMemberRole role;
    }

    @Data
    public static class UpdateUserBody {
        private String loginName;

        private String email;
        private String phone;
        private String displayName;
        private String password;
        private UserAccountStatus status;
    }

    @Data
    public static class UpdateTenantRoleBody {
        /** 仅创始人可指定，用于在其它租户下调整成员角色。 */
        private Long tenantId;

        private TenantMemberRole role;
    }
}
