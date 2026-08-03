package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.identity.AccountPrincipalPaths;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.tenant.TenantMemberRoleApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 REST：租户成员列表与邀请/移除。
 *
 * <p>协作与「逻辑变更须同步注释」见 {@code .cursorrules} §7.2；权限、审计与错误码契约见 {@code PROJECT.md}「管理端租户成员与审计写入规则（约定）」。改角色亦可经 {@code
 * AdminUserRestController} 委托同一应用服务。
 */
@Tag(name = "管理端-租户成员", description = "成员列表、邀请/恢复、移出租户、角色调整（部分能力亦见 admin/users）")
@RestController
@RequiredArgsConstructor
public class AdminTenantMembersRestController extends ApiV1ControllerBases.AdminTenantMembers {

    private final TenantMemberRoleApplicationService tenantMemberRoleApplicationService;

    @Operation(summary = "成员列表", description = "默认仅 ACTIVE；includeInactive 仅所有者/创始人可用。")
    @GetMapping
    public List<TenantMemberRoleApplicationService.TenantMemberRow> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) TenantMemberRole role,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return tenantMemberRoleApplicationService.listMembers(tenantId, role, includeInactive);
    }

    @Operation(
            summary = "邀请或恢复成员",
            description = "按登录名（loginName，全局唯一）定位用户；目标账号须启用；已有在册关系时返回冲突错误码。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantMemberRoleApplicationService.TenantMemberRow invite(@Valid @RequestBody InviteTenantMemberBody body) {
        return tenantMemberRoleApplicationService.inviteMember(
                body.getLoginName(), body.getRole(), body.getTenantId());
    }

    @Operation(
            summary = "从租户移除成员",
            description = "路径为账号主体（如 ln:登录名）；将成员行置为已退出（非删除用户账号）。")
    @DeleteMapping("/{account}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable String account, @RequestParam(required = false) Long tenantId) {
        tenantMemberRoleApplicationService.removeMemberFromTenant(
                AccountPrincipalPaths.parsePathSegment(account), tenantId);
    }

    @Data
    public static class InviteTenantMemberBody {
        @NotBlank
        private String loginName;
        private TenantMemberRole role;
        /** 仅创始人可指定 */
        private Long tenantId;
    }
}
