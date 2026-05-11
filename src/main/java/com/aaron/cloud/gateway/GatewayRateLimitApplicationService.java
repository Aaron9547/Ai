package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.gateway.GwApiRateLimitRuleRepository;
import com.aaron.cloud.common.gateway.entity.GwApiRateLimitRule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayRateLimitApplicationService {

    private final GwApiRateLimitRuleRepository ruleRepository;

    /**
     * 管理端分页。
     *
     * <p>非创始人：固定为当前 JWT 租户下的规则（不含 {@code tenant_id IS NULL} 全局限流）。
     *
     * <p>创始人：{@code rateScope=GLOBAL} 时仅全局限流；否则按 {@code tenantId}（缺省为当前 JWT 租户）筛选租户级规则。
     */
    public Page<GwApiRateLimitRule> page(long pageNo, long pageSize, String rateScope, Long tenantId) {
        TenantSnapshot snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (role != null && role.isFounder()) {
            if ("GLOBAL".equalsIgnoreCase(rateScope)) {
                return ruleRepository.pageWhereTenantIdIsNull(pageNo, pageSize);
            }
            long tid = tenantId != null ? tenantId : snap.getTenantId();
            return ruleRepository.pageForTenant(tid, pageNo, pageSize);
        }
        return ruleRepository.pageForTenant(snap.getTenantId(), pageNo, pageSize);
    }

    @Transactional
    public GwApiRateLimitRule create(
            Long tenantId, String pathPattern, String httpMethod, int requestsPerMinute, ToggleState enabled, String remark) {
        TenantSnapshot snap = TenantContextHolder.require();
        normalizeTenantScope(snap, tenantId);
        Long tid = resolvePersistedTenantId(snap, tenantId);
        var row = new GwApiRateLimitRule();
        row.setTenantId(tid);
        row.setPathPattern(pathPattern == null ? "" : pathPattern.trim());
        row.setHttpMethod(httpMethod == null || httpMethod.isBlank() ? "*" : httpMethod.trim().toUpperCase());
        row.setRequestsPerMinute(requestsPerMinute);
        row.setEnabled(enabled == null ? ToggleState.OFF : enabled);
        row.setRemark(remark);
        ruleRepository.insert(row);
        return Objects.requireNonNull(ruleRepository.findById(row.getId()));
    }

    @Transactional
    public GwApiRateLimitRule update(
            long id,
            Long tenantId,
            String pathPattern,
            String httpMethod,
            Integer requestsPerMinute,
            ToggleState enabled,
            String remark) {
        TenantSnapshot snap = TenantContextHolder.require();
        GwApiRateLimitRule row = ruleRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("rule not found");
        }
        assertRowVisible(snap, row);
        if (tenantId != null) {
            normalizeTenantScope(snap, tenantId);
            row.setTenantId(resolvePersistedTenantId(snap, tenantId));
        }
        if (pathPattern != null) {
            row.setPathPattern(pathPattern.trim());
        }
        if (httpMethod != null) {
            row.setHttpMethod(httpMethod.isBlank() ? "*" : httpMethod.trim().toUpperCase());
        }
        if (requestsPerMinute != null) {
            row.setRequestsPerMinute(Math.max(1, requestsPerMinute));
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        if (remark != null) {
            row.setRemark(remark);
        }
        ruleRepository.updateById(row);
        return Objects.requireNonNull(ruleRepository.findById(id));
    }

    @Transactional
    public void delete(long id) {
        TenantSnapshot snap = TenantContextHolder.require();
        GwApiRateLimitRule row = ruleRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("rule not found");
        }
        assertRowVisible(snap, row);
        ruleRepository.deleteById(id);
    }

    private static void assertRowVisible(TenantSnapshot snap, GwApiRateLimitRule row) {
        TenantMemberRole role = snap.getMemberRole();
        if (role != null && role.isFounder()) {
            return;
        }
        if (row.getTenantId() == null) {
            throw new AccessDeniedException("仅创始人可调整全局限流规则");
        }
        if (!row.getTenantId().equals(snap.getTenantId())) {
            throw new AccessDeniedException("不可操作其他租户的限流规则");
        }
    }

    private static void normalizeTenantScope(TenantSnapshot snap, Long requestedTenantId) {
        TenantMemberRole role = snap.getMemberRole();
        if (role != null && role.isFounder()) {
            return;
        }
        if (requestedTenantId != null && !requestedTenantId.equals(snap.getTenantId())) {
            throw new AccessDeniedException("不可为其他租户创建限流规则");
        }
    }

    private static Long resolvePersistedTenantId(TenantSnapshot snap, Long requestedTenantId) {
        TenantMemberRole role = snap.getMemberRole();
        if (role != null && role.isFounder()) {
            return requestedTenantId;
        }
        return snap.getTenantId();
    }
}
