package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.dto.identity.AccountPrincipalView;
import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
import com.aaron.cloud.common.api.identity.AccountPrincipalRef;
import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.identity.account.AccountPrincipalResolver;
import com.aaron.cloud.identity.account.AccountSelfOperationGuard;
import com.aaron.cloud.identity.admin.UserAccessPresenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 管理端租户成员：列表、邀请/恢复、移出租户、改角色。
 *
 * <p><b>规则真源</b>：协作与注释维护见仓库根目录 {@code .cursorrules} <b>§7.2</b>；权限矩阵、审计字段、{@code EX_MSG_*} 与 {@code GlobalExceptionHandler} 契约等长文约定见
 * {@code PROJECT.md}「<b>管理端租户成员与审计写入规则（约定）</b>」。修改本类分支条件或异常消息时，须<strong>同步更新</strong>上述两处相关表述与本类注释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMemberRoleApplicationService {

    private final SysTenantMemberRepository tenantMemberRepository;
    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantRepository tenantRepository;
    private final SysAuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final UserAccessPresenceService userAccessPresenceService;
    private final AccountPrincipalResolver accountPrincipalResolver;

    /**
     * 管理端成员列表；创始人可传 {@code tenantId} 查任意租户，否则使用当前租户上下文。
     *
     * @param tenantId 非创始人不得传入或与上下文不一致
     * @param role 非空时仅返回该角色的成员行
     * @param includeInactive 为 true 时包含已退出成员；仅所有者或创始人可用
     */
    public List<TenantMemberRow> listMembers(
            Long tenantId, TenantMemberRole role, boolean includeInactive) {
        var snap = TenantContextHolder.require();
        TenantMemberRole caller = snap.getMemberRole();
        if (caller == null) {
            throw new AccessDeniedException("无权查看成员列表");
        }
        if (includeInactive && !caller.isFounder() && caller != TenantMemberRole.OWNER) {
            throw new AccessDeniedException("仅所有者或创始人可查看已退出成员");
        }
        long contextTid = snap.getTenantId();
        long tid;
        if (tenantId != null) {
            if (!caller.isFounder()) {
                throw new AccessDeniedException("仅创始人可按租户筛选成员");
            }
            tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
            tid = tenantId;
        } else {
            tid = contextTid;
        }
        if (!caller.isFounder() && tid != contextTid) {
            throw new AccessDeniedException("仅能查询当前租户成员");
        }
        // 默认「仅在册」：传 ACTIVE 给仓储；includeInactive 时传 null 表示不按成员状态过滤（见 PROJECT.md 第 3 节与列表默认行为）。
        UserAccountStatus statusFilter = includeInactive ? null : UserAccountStatus.ACTIVE;
        List<SysTenantMember> rows = tenantMemberRepository.listByTenantAndRoleAndStatus(tid, role, statusFilter);
        List<SecUserAccount> accounts = new ArrayList<>();
        List<SysTenantMember> memberRows = new ArrayList<>();
        for (SysTenantMember m : rows) {
            SecUserAccount u = userAccountRepository.findById(m.getUserId()).orElse(null);
            if (u == null) {
                continue;
            }
            memberRows.add(m);
            accounts.add(u);
        }
        List<Long> userIds = accounts.stream().map(SecUserAccount::getId).toList();
        var online = userAccessPresenceService.onlineAmong(tid, userIds);
        List<TenantMemberRow> out = new ArrayList<>(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            out.add(toMemberRow(memberRows.get(i), accounts.get(i), online.contains(accounts.get(i).getId())));
        }
        return out;
    }

    /**
     * 邀请或恢复成员：按<strong>登录名</strong>（{@link SecUserAccount#getLoginName()}，库侧 {@code uk_sec_user_login_name}
     * 全局唯一）解析目标用户；目标用户在目标租户下无在册（ACTIVE）关系时可写入或激活；仅所有者或创始人。
     */
    public TenantMemberRow inviteMember(String loginName, TenantMemberRole newRole, Long tenantIdOverride) {
        if (newRole == null) {
            throw new IllegalArgumentException("role required");
        }
        if (loginName == null || loginName.isBlank()) {
            throw new IllegalArgumentException("请填写登录名");
        }
        String trimmedLogin = loginName.trim();
        var snap = TenantContextHolder.require();
        assertOwnerOrFounder(snap.getMemberRole());
        long tid = resolveTargetTenantId(snap, tenantIdOverride);
        SecUserAccount user =
                userAccountRepository
                        .findByLoginIdentifier(trimmedLogin)
                        .orElseThrow(() -> new IllegalArgumentException("未找到该登录名的用户"));
        long userId = user.getId();
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("user account not active");
        }
        AccountSelfOperationGuard.assertNotSelf(AccountPrincipalRef.loginName(trimmedLogin), trimmedLogin);

        var existing = tenantMemberRepository.find(tid, userId);
        // 先统一做 OWNER/FOUNDER 对目标角色的约束，再区分「已 ACTIVE 冲突」与「DISABLED 恢复」。
        assertTenantRoleAssignmentRules(snap.getMemberRole(), existing.orElse(null), newRole);
        if (existing.isPresent()) {
            SysTenantMember row = existing.get();
            if (row.getStatus() == UserAccountStatus.ACTIVE) {
                // 须抛 ErrorCodes.EX_MSG_* 字面量，供 GlobalExceptionHandler 映射为 TENANT_MEMBER_ALREADY_ACTIVE（409）。
                throw new IllegalStateException(ErrorCodes.EX_MSG_TENANT_MEMBER_ALREADY_ACTIVE);
            }
            row.setStatus(UserAccountStatus.ACTIVE);
            row.setRoleCode(newRole);
            tenantMemberRepository.updateById(row);
            auditAppend(
                    tid,
                    "TENANT_MEMBER_INVITE",
                    "sys_tenant_member",
                    String.valueOf(row.getId()),
                    Map.of(
                            "userId",
                            userId,
                            "loginName",
                            trimmedLogin,
                            "role",
                            newRole.name(),
                            "reactivated",
                            true));
            return toRow(row, user);
        }

        var m = new SysTenantMember();
        m.setTenantId(tid);
        m.setUserId(userId);
        m.setRoleCode(newRole);
        m.setStatus(UserAccountStatus.ACTIVE);
        tenantMemberRepository.insert(m);
        auditAppend(
                tid,
                "TENANT_MEMBER_INVITE",
                "sys_tenant_member",
                String.valueOf(m.getId()),
                    Map.of(
                            "userId",
                            userId,
                            "loginName",
                            trimmedLogin,
                            "role",
                            newRole.name(),
                            "reactivated",
                            false));
        return toRow(m, user);
    }

    /** 将成员从租户移除（成员行置为已退出）；仅所有者或创始人。 */
    public void removeMemberFromTenant(AccountPrincipalRef targetAccount, Long tenantIdOverride) {
        SecUserAccount user = accountPrincipalResolver.requireAccount(targetAccount);
        removeMemberFromTenant(user.getId(), tenantIdOverride, user);
    }

    public void removeMemberFromTenant(long targetUserId, Long tenantIdOverride) {
        var user =
                userAccountRepository
                        .findById(targetUserId)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
        removeMemberFromTenant(targetUserId, tenantIdOverride, user);
    }

    private void removeMemberFromTenant(long targetUserId, Long tenantIdOverride, SecUserAccount user) {
        var snap = TenantContextHolder.require();
        assertOwnerOrFounder(snap.getMemberRole());
        long tid = resolveTargetTenantId(snap, tenantIdOverride);
        AccountSelfOperationGuard.assertNotSelf(user);
        SysTenantMember row =
                tenantMemberRepository
                        .find(tid, targetUserId)
                        .orElseThrow(() -> new IllegalArgumentException("用户不在目标租户"));
        if (row.getStatus() != UserAccountStatus.ACTIVE) {
            throw new IllegalArgumentException(ErrorCodes.EX_MSG_TENANT_MEMBER_INACTIVE);
        }
        // 移除前仍校验 OWNER 对 FOUNDER/OWNER 行的限制；priorRole 取自尚未改 roleCode 的 row。
        assertOwnerMutateRow(snap.getMemberRole(), row, row.getRoleCode());
        row.setStatus(UserAccountStatus.DISABLED);
        tenantMemberRepository.updateById(row);
        auditAppend(
                tid,
                "TENANT_MEMBER_REMOVE",
                "sys_tenant_member",
                String.valueOf(row.getId()),
                Map.of("userId", targetUserId, "priorRole", row.getRoleCode().name()));
    }

    /**
     * 调整成员在指定租户内的角色；{@code tenantIdOverride} 仅创始人可传，用于跨租户管理。
     */
    public void updateMemberRole(AccountPrincipalRef targetAccount, TenantMemberRole newRole, Long tenantIdOverride) {
        SecUserAccount user = accountPrincipalResolver.requireAccount(targetAccount);
        updateMemberRole(user.getId(), newRole, tenantIdOverride, user);
    }

    public void updateMemberRole(long targetUserId, TenantMemberRole newRole, Long tenantIdOverride) {
        SecUserAccount user =
                userAccountRepository
                        .findById(targetUserId)
                        .orElseThrow(() -> new IllegalArgumentException("user not found"));
        updateMemberRole(targetUserId, newRole, tenantIdOverride, user);
    }

    private void updateMemberRole(
            long targetUserId, TenantMemberRole newRole, Long tenantIdOverride, SecUserAccount user) {
        if (newRole == null) {
            throw new IllegalArgumentException("role required");
        }
        var snap = TenantContextHolder.require();
        assertOwnerOrFounder(snap.getMemberRole());
        long tid = resolveTargetTenantId(snap, tenantIdOverride);
        AccountSelfOperationGuard.assertNotSelf(user);
        SysTenantMember row =
                tenantMemberRepository
                        .find(tid, targetUserId)
                        .orElseThrow(() -> new IllegalArgumentException("用户不在目标租户"));
        if (row.getStatus() != UserAccountStatus.ACTIVE) {
            throw new IllegalArgumentException(ErrorCodes.EX_MSG_TENANT_MEMBER_INACTIVE);
        }

        TenantMemberRole caller = snap.getMemberRole();
        assertOwnerMutateRow(caller, row, newRole);
        if (newRole == TenantMemberRole.FOUNDER && (caller == null || !caller.isFounder())) {
            throw new AccessDeniedException("仅创始人可分配创始人角色");
        }

        TenantMemberRole oldRole = row.getRoleCode();
        row.setRoleCode(newRole);
        tenantMemberRepository.updateById(row);
        auditAppend(
                tid,
                "TENANT_MEMBER_ROLE_UPDATE",
                "sys_tenant_member",
                String.valueOf(row.getId()),
                Map.of("userId", targetUserId, "from", oldRole.name(), "to", newRole.name()));
    }

    private static void assertOwnerOrFounder(TenantMemberRole caller) {
        if (caller == null || (!caller.isFounder() && caller != TenantMemberRole.OWNER)) {
            throw new AccessDeniedException("仅租户所有者或创始人可管理成员");
        }
    }

    /**
     * 写操作目标租户：非创始人一律用 JWT 上下文租户；仅创始人可用 {@code tenantIdOverride} 跨租户（须存在）。
     *
     * <p>创始人未传覆盖时仍用上下文租户（例如 UI 在「当前工作区」下操作），与 PROJECT.md「目标租户 tid 解析」一致。
     */
    private long resolveTargetTenantId(TenantSnapshot snap, Long tenantIdOverride) {
        TenantMemberRole caller = snap.getMemberRole();
        long contextTid = snap.getTenantId();
        if (tenantIdOverride != null) {
            if (!caller.isFounder()) {
                throw new AccessDeniedException("仅创始人可指定目标租户");
            }
            tenantRepository
                    .findById(tenantIdOverride)
                    .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
            return tenantIdOverride;
        }
        if (!caller.isFounder() && contextTid <= 0) {
            throw new AccessDeniedException("缺少租户上下文");
        }
        if (!caller.isFounder()) {
            return contextTid;
        }
        return contextTid;
    }

    /**
     * 所有者不能改创始人/其它所有者，也不能把他人设为创始人或所有者；创始人不受此条限制（除分配创始人需 isFounder）。
     */
    private static void assertOwnerMutateRow(
            TenantMemberRole caller, SysTenantMember existingRow, TenantMemberRole newRole) {
        if (caller != TenantMemberRole.OWNER) {
            return;
        }
        if (existingRow != null) {
            if (existingRow.getRoleCode() == TenantMemberRole.FOUNDER
                    || existingRow.getRoleCode() == TenantMemberRole.OWNER) {
                throw new AccessDeniedException("所有者不能变更创始人或其它所有者的成员关系");
            }
        }
        if (newRole == TenantMemberRole.FOUNDER || newRole == TenantMemberRole.OWNER) {
            throw new AccessDeniedException("所有者不能分配创始人或所有者角色");
        }
    }

    private static void assertTenantRoleAssignmentRules(
            TenantMemberRole caller, SysTenantMember existingRow, TenantMemberRole newRole) {
        assertOwnerMutateRow(caller, existingRow, newRole);
        if (newRole == TenantMemberRole.FOUNDER && (caller == null || !caller.isFounder())) {
            throw new AccessDeniedException("仅创始人可分配创始人角色");
        }
    }

    private TenantMemberRow toRow(SysTenantMember m, SecUserAccount u) {
        boolean online =
                userAccessPresenceService
                        .onlineAmong(m.getTenantId(), java.util.List.of(u.getId()))
                        .contains(u.getId());
        return toMemberRow(m, u, online);
    }

    private TenantMemberRow toMemberRow(SysTenantMember m, SecUserAccount u, boolean sessionOnline) {
        String lastLoginAt = BeijingTime.formatDisplay(u.getLastLoginAt());
        String registeredAt = BeijingTime.formatDisplay(u.getRegisteredAt());
        return new TenantMemberRow(
                m.getId(),
                AccountPrincipalView.fromAccountNo(u.getAccountNo()),
                u.getAccountNo(),
                u.getLoginName(),
                u.getEmail(),
                u.getPhone(),
                u.getRegistrationChannel(),
                registeredAt,
                u.getDisplayName() == null ? "" : u.getDisplayName(),
                u.getStatus(),
                m.getTenantId(),
                m.getRoleCode(),
                m.getStatus(),
                sessionOnline,
                lastLoginAt,
                u.getLastLoginIp(),
                u.getLastLoginRegion());
    }

    /**
     * 追加业务审计：字段与 {@code detail_json} 键名须与 PROJECT.md「sys_audit_event 落库字段」一致；失败仅 WARN，不阻断主流程。
     */
    private void auditAppend(long tenantId, String action, String resourceType, String resourceId, Map<String, ?> detail) {
        try {
            var snap = TenantContextHolder.require();
            Long uid = snap.getUserId();
            SysAuditEvent e = new SysAuditEvent();
            e.setTenantId(tenantId);
            e.setActorType("USER");
            e.setActorId(uid == null ? "" : String.valueOf(uid));
            e.setAction(action);
            e.setResourceType(resourceType);
            e.setResourceId(resourceId);
            e.setDetailJson(writeDetail(detail));
            auditEventRepository.insert(e);
        } catch (Exception ex) {
            log.warn("audit append failed action={} tenantId={}", action, tenantId, ex);
        }
    }

    private String writeDetail(Map<String, ?> detail) throws JsonProcessingException {
        return objectMapper.writeValueAsString(detail == null ? Map.of() : new LinkedHashMap<>(detail));
    }

    public record TenantMemberRow(
            long memberId,
            AccountPrincipalView account,
            String accountNo,
            String loginName,
            String email,
            String phone,
            UserRegistrationChannel registrationChannel,
            String registeredAt,
            String displayName,
            UserAccountStatus userStatus,
            long tenantId,
            TenantMemberRole role,
            UserAccountStatus memberStatus,
            boolean sessionOnline,
            String lastLoginAt,
            String lastLoginIp,
            String lastLoginRegion) {}
}
