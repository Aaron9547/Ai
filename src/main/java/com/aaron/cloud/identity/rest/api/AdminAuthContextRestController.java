package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.ErrorCodes;
import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.aaron.cloud.common.context.LoginContextUtils;
import com.aaron.cloud.common.context.LoginUser;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.web.ApiErrorResponse;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.jwt.JwtLocalAdminTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端切换工作区（租户 + 角色）并换发 JWT。
 *
 * <p>成功换发后写入 {@code sys_audit_event}（{@code action=ADMIN_CONTEXT_SWITCH}）；字段约定见 {@code PROJECT.md}「管理端租户成员与审计写入规则」第 4 节。注释与逻辑同步义务见
 * {@code .cursorrules} §7.2。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.providers.auth", havingValue = "jwt-local")
public class AdminAuthContextRestController extends ApiV1ControllerBases.Auth {

    private final SysTenantMemberRepository tenantMemberRepository;
    private final SysTenantRepository tenantRepository;
    private final JwtLocalAdminTokenService jwtLocalAdminTokenService;
    private final SysAuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /** 为当前登录用户按选定租户与角色签发新 JWT（声明与登录接口一致）。 */
    @PostMapping(value = "/admin-context", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> switchAdminContext(@RequestBody AdminContextRequest body) throws Exception {
        if (body.getRole() == null) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiErrorResponse.builder().code(ErrorCodes.VALIDATION).message("role required").build());
        }
        if (body.getTenantId() <= 0) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiErrorResponse.builder().code(ErrorCodes.VALIDATION).message("tenantId invalid").build());
        }

        LoginUser user;
        try {
            user = LoginContextUtils.requireUser();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            ApiErrorResponse.builder()
                                    .code(ErrorCodes.LOGIN_ACCOUNT_DISABLED)
                                    .message("account not active")
                                    .build());
        }

        List<SysTenantMember> memberships = tenantMemberRepository.listByUserId(user.getId());
        List<SysTenantMember> active =
                memberships.stream().filter(m -> m.getStatus() == UserAccountStatus.ACTIVE).toList();

        boolean founder =
                active.stream().anyMatch(m -> m.getRoleCode() == TenantMemberRole.FOUNDER);
        long tid = body.getTenantId();
        TenantMemberRole requested = body.getRole();

        if (founder) {
            if (requested != TenantMemberRole.FOUNDER) {
                return denied();
            }
            if (tenantRepository.findById(tid).isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                ApiErrorResponse.builder().code(ErrorCodes.NOT_FOUND).message("tenant not found").build());
            }
            var minted =
                    jwtLocalAdminTokenService.mintExplicitContext(user, active, tid, TenantMemberRole.FOUNDER);
            auditContextSwitch(user.getId(), tid, TenantMemberRole.FOUNDER);
            return ResponseEntity.ok(minted);
        }

        boolean match =
                active.stream()
                        .anyMatch(m -> m.getTenantId() == tid && m.getRoleCode() == requested);
        if (!match) {
            return denied();
        }

        var minted = jwtLocalAdminTokenService.mintExplicitContext(user, active, tid, requested);
        auditContextSwitch(user.getId(), tid, requested);
        return ResponseEntity.ok(minted);
    }

    /** 与成员审计同属一张表；{@code tenant_id} 为切换后的工作租户，{@code detail_json} 仅含切换后角色名。 */
    private void auditContextSwitch(long userId, long tenantId, TenantMemberRole role) {
        try {
            SysAuditEvent e = new SysAuditEvent();
            e.setTenantId(tenantId);
            e.setActorType("USER");
            e.setActorId(String.valueOf(userId));
            e.setAction("ADMIN_CONTEXT_SWITCH");
            e.setResourceType("admin_context");
            e.setResourceId(String.valueOf(tenantId));
            e.setDetailJson(objectMapper.writeValueAsString(Map.of("role", role.name())));
            auditEventRepository.insert(e);
        } catch (Exception ex) {
            log.warn("audit append failed action=ADMIN_CONTEXT_SWITCH tenantId={}", tenantId, ex);
        }
    }

    private static ResponseEntity<ApiErrorResponse> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        ApiErrorResponse.builder()
                                .code(ErrorCodes.ADMIN_CONTEXT_DENIED)
                                .message("no membership for tenant and role")
                                .build());
    }

    @Data
    public static class AdminContextRequest {
        private long tenantId;
        private TenantMemberRole role;
    }
}
