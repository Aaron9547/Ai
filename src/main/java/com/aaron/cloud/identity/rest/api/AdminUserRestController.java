package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.admin.UserAdminMenuApplicationService;
import com.aaron.cloud.identity.tenant.TenantMemberRoleApplicationService;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequiredArgsConstructor
public class AdminUserRestController extends ApiV1ControllerBases.AdminUsers {

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAdminMenuApplicationService userAdminMenuApplicationService;
    private final TenantMemberRoleApplicationService tenantMemberRoleApplicationService;

    @GetMapping
    public List<UserView> page(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        var snap = TenantContextHolder.require();
        return tenantMemberRepository.pageByTenant(snap.getTenantId(), page, size).getRecords().stream()
                .map(
                        m -> {
                            var u =
                                    userAccountRepository
                                            .findById(m.getUserId())
                                            .orElse(null);
                            if (u == null) {
                                return null;
                            }
                            TenantMemberRole tr =
                                    m.getStatus() == UserAccountStatus.ACTIVE ? m.getRoleCode() : null;
                            return new UserView(
                                    u.getId(), u.getLoginName(), u.getDisplayName(), u.getStatus(), tr);
                        })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @GetMapping("/{id}/admin-menus")
    public List<String> listUserAdminMenus(@PathVariable long id) {
        assertTargetUserInCurrentTenant(id);
        return userAdminMenuApplicationService.listForUser(id);
    }

    @PutMapping("/{id}/admin-menus")
    public void replaceUserAdminMenus(@PathVariable long id, @RequestBody ReplaceUserAdminMenusBody body) {
        assertTargetUserInCurrentTenant(id);
        userAdminMenuApplicationService.replaceForUser(id, body.getMenuCodes());
    }

    @PutMapping("/{id}/tenant-role")
    public UserView updateTenantRole(@PathVariable long id, @RequestBody UpdateTenantRoleBody body) {
        var snap = TenantContextHolder.require();
        if (body.getTenantId() != null
                && (snap.getMemberRole() == null || !snap.getMemberRole().isFounder())) {
            throw new AccessDeniedException("仅创始人可指定 tenantId");
        }
        if (body.getTenantId() == null) {
            assertTargetUserInCurrentTenant(id);
        }
        tenantMemberRoleApplicationService.updateMemberRole(id, body.getRole(), body.getTenantId());
        var u =
                userAccountRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
        long tidForRead =
                body.getTenantId() != null ? body.getTenantId() : TenantContextHolder.require().getTenantId();
        TenantMemberRole tenantRole =
                tenantMemberRepository.find(tidForRead, id).map(SysTenantMember::getRoleCode).orElse(null);
        return new UserView(u.getId(), u.getLoginName(), u.getDisplayName(), u.getStatus(), tenantRole);
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable long id) {
        var snap = TenantContextHolder.require();
        SysTenantMember membership =
                tenantMemberRepository
                        .find(snap.getTenantId(), id)
                        .orElseThrow(() -> new AccessDeniedException("用户不在当前租户"));
        var u =
                userAccountRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
        TenantMemberRole tenantRole =
                membership.getStatus() == UserAccountStatus.ACTIVE ? membership.getRoleCode() : null;
        return new UserView(u.getId(), u.getLoginName(), u.getDisplayName(), u.getStatus(), tenantRole);
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
        var u = new SecUserAccount();
        u.setLoginName(loginName);
        u.setDisplayName(body.getDisplayName() == null ? "" : body.getDisplayName());
        u.setPasswordHash(passwordEncoder.encode(body.getPassword()));
        u.setStatus(UserAccountStatus.ACTIVE);
        userAccountRepository.insert(u);
        var m = new SysTenantMember();
        m.setTenantId(snap.getTenantId());
        m.setUserId(u.getId());
        m.setRoleCode(body.getRole() == null ? TenantMemberRole.MEMBER : body.getRole());
        m.setStatus(UserAccountStatus.ACTIVE);
        tenantMemberRepository.insert(m);
        TenantMemberRole role = body.getRole() == null ? TenantMemberRole.MEMBER : body.getRole();
        return new UserView(u.getId(), u.getLoginName(), u.getDisplayName(), u.getStatus(), role);
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable long id, @RequestBody UpdateUserBody body) {
        assertTargetUserInCurrentTenant(id);
        var u =
                userAccountRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
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
            if (userAccountRepository.existsLoginName(body.getLoginName().trim(), id)) {
                throw new IllegalStateException(ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT);
            }
            u.setLoginName(body.getLoginName().trim());
        }
        userAccountRepository.updateById(u);
        TenantMemberRole tenantRole =
                tenantMemberRepository
                        .find(TenantContextHolder.require().getTenantId(), id)
                        .map(
                                m ->
                                        m.getStatus() == UserAccountStatus.ACTIVE
                                                ? m.getRoleCode()
                                                : null)
                        .orElse(null);
        return new UserView(u.getId(), u.getLoginName(), u.getDisplayName(), u.getStatus(), tenantRole);
    }

    @PostMapping("/{id}/kick-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kickSession(@PathVariable long id) {
        assertCanManageTargetUser(id);
        userAccountRepository.incrementJwtSeq(id);
    }

    /**
     * 封禁：账号与当前租户成员关系置为禁用，并立即使 JWT 失效（等同「禁用 + 踢下线」）。
     */
    @PostMapping("/{id}/ban")
    public UserView ban(@PathVariable long id) {
        assertCanManageTargetUser(id);
        var snap = TenantContextHolder.require();
        var u =
                userAccountRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
        u.setStatus(UserAccountStatus.DISABLED);
        userAccountRepository.updateById(u);
        tenantMemberRepository
                .find(snap.getTenantId(), id)
                .ifPresent(m -> tenantMemberRepository.updateById(deletedMember(m)));
        userAccountRepository.incrementJwtSeq(id);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        assertTargetUserInCurrentTenant(id);
        var snap = TenantContextHolder.require();
        tenantMemberRepository
                .find(snap.getTenantId(), id)
                .ifPresent(m -> tenantMemberRepository.updateById(deletedMember(m)));
        userAccountRepository.findById(id).ifPresent(u -> {
            u.setStatus(UserAccountStatus.DISABLED);
            userAccountRepository.updateById(u);
        });
    }

    private static SysTenantMember deletedMember(SysTenantMember m) {
        m.setStatus(UserAccountStatus.DISABLED);
        return m;
    }

    private void assertTargetUserInCurrentTenant(long targetUserId) {
        var snap = TenantContextHolder.require();
        tenantMemberRepository
                .find(snap.getTenantId(), targetUserId)
                .orElseThrow(() -> new AccessDeniedException("用户不在当前租户"));
    }

    private void assertCanManageTargetUser(long targetUserId) {
        var snap = TenantContextHolder.require();
        if (snap.getUserId() != null && snap.getUserId() == targetUserId) {
            throw new IllegalArgumentException("cannot operate on self");
        }
        TenantMemberRole role = snap.getMemberRole();
        if (role != null && role.isFounder()) {
            tenantMemberRepository
                    .find(snap.getTenantId(), targetUserId)
                    .orElseThrow(() -> new IllegalArgumentException("user not in tenant"));
            return;
        }
        tenantMemberRepository
                .find(snap.getTenantId(), targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("user not in tenant"));
    }

    public record UserView(
            long id,
            String loginName,
            String displayName,
            UserAccountStatus status,
            TenantMemberRole tenantRole) {}

    @Data
    public static class ReplaceUserAdminMenusBody {
        private List<String> menuCodes;
    }

    @Data
    public static class CreateUserBody {
        @NotBlank private String loginName;

        private String displayName;
        @NotBlank private String password;
        private TenantMemberRole role;
    }

    @Data
    public static class UpdateUserBody {
        private String loginName;

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
