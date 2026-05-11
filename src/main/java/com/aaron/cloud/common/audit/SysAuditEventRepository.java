package com.aaron.cloud.common.audit;

import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.aaron.cloud.common.audit.mapper.SysAuditEventMapper;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SysAuditEventRepository {

    private final SysAuditEventMapper mapper;
    private final SysTenantRepository sysTenantRepository;
    private final SecUserAccountRepository secUserAccountRepository;
    private final ObjectMapper objectMapper;

    public Page<SysAuditEvent> pageByTenant(long tenantId, long pageNo, long pageSize) {
        Page<SysAuditEvent> page =
                mapper.selectPage(
                        Page.of(pageNo, pageSize),
                        Wrappers.<SysAuditEvent>lambdaQuery()
                                .eq(SysAuditEvent::getTenantId, tenantId)
                                .orderByDesc(SysAuditEvent::getCreatedAt));
        attachAdminDisplayFields(page.getRecords());
        return page;
    }

    /** {@code filterTenantId == null} 时不按租户过滤（创始人全量列表）。 */
    public Page<SysAuditEvent> pageForAdmin(Long filterTenantIdOrNull, long pageNo, long pageSize) {
        var q = Wrappers.<SysAuditEvent>lambdaQuery().orderByDesc(SysAuditEvent::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(SysAuditEvent::getTenantId, filterTenantIdOrNull);
        }
        Page<SysAuditEvent> page = mapper.selectPage(Page.of(pageNo, pageSize), q);
        attachAdminDisplayFields(page.getRecords());
        return page;
    }

    private void attachAdminDisplayFields(List<SysAuditEvent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> tenantIds =
                records.stream()
                        .map(SysAuditEvent::getTenantId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        var tenants = sysTenantRepository.mapTenantsByIds(tenantIds);
        List<Long> detailMemberUserIds = new ArrayList<>();
        for (SysAuditEvent r : records) {
            if (r.getResourceType() != null && r.getResourceType().equalsIgnoreCase("sys_tenant_member")) {
                Long du = parseDetailUserId(r.getDetailJson());
                if (du != null) {
                    detailMemberUserIds.add(du);
                }
            }
        }
        List<Long> actorUserIds =
                records.stream()
                        .map(SysAuditEvent::parseActorUserIdIfUser)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        List<Long> mergedUserIds =
                Stream.concat(actorUserIds.stream(), detailMemberUserIds.stream())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, String> userLabels = secUserAccountRepository.mapUserDisplayLabelByIds(mergedUserIds);
        for (SysAuditEvent r : records) {
            if (r.getTenantId() != null) {
                var t = tenants.get(r.getTenantId());
                if (t != null) {
                    r.setTenantCode(t.getCode());
                    r.setTenantName(t.getName());
                }
            }
            Long uid = r.parseActorUserIdIfUser();
            if (uid != null) {
                r.setActorDisplayName(userLabels.get(uid));
            }
        }
        attachResourceDisplaySummaries(records, userLabels);
    }

    /**
     * 成员类审计 {@code detail_json} 中的 {@code userId}（与 PROJECT.md 约定一致）；无则 {@code null}。
     */
    private Long parseDetailUserId(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailJson);
            JsonNode n = root.get("userId");
            if (n == null || n.isNull()) {
                return null;
            }
            if (n.isIntegralNumber()) {
                return n.longValue();
            }
            if (n.isTextual()) {
                return Long.parseLong(n.asText().trim());
            }
        } catch (Exception ex) {
            log.warn("audit detail userId parse failed", ex);
        }
        return null;
    }

    /**
     * 管理端「关联对象」摘要：成员类事件以 {@code detail_json.userId} 关联 {@code sec_user_account} 展示名（与操作者同源）；上下文切换沿用租户名/编码。
     */
    private void attachResourceDisplaySummaries(List<SysAuditEvent> records, Map<Long, String> userLabels) {
        for (SysAuditEvent r : records) {
            String summary = null;
            String rt = r.getResourceType();
            if (rt != null && rt.equalsIgnoreCase("sys_tenant_member")) {
                Long detailUid = parseDetailUserId(r.getDetailJson());
                if (detailUid != null) {
                    String lab = userLabels.get(detailUid);
                    if (lab != null && !lab.isBlank()) {
                        summary = "成员 · " + lab.trim();
                    }
                }
                if (summary == null && r.getDetailJson() != null && !r.getDetailJson().isBlank()) {
                    try {
                        JsonNode root = objectMapper.readTree(r.getDetailJson());
                        JsonNode ln = root.get("loginName");
                        if (ln != null && !ln.asText().isBlank()) {
                            summary = "成员 · " + ln.asText().trim();
                        }
                    } catch (Exception ex) {
                        log.warn(
                                "audit resource summary loginName fallback failed id={} action={}",
                                r.getId(),
                                r.getAction(),
                                ex);
                    }
                }
            } else if (rt != null && rt.equalsIgnoreCase("admin_context")) {
                String tn = r.getTenantName();
                String tc = r.getTenantCode();
                if ((tn != null && !tn.isBlank()) || (tc != null && !tc.isBlank())) {
                    if (tn != null && !tn.isBlank() && tc != null && !tc.isBlank()) {
                        summary = tn.trim() + "（" + tc.trim() + "）· 工作区";
                    } else if (tn != null && !tn.isBlank()) {
                        summary = tn.trim() + " · 工作区";
                    } else {
                        summary = tc.trim() + " · 工作区";
                    }
                }
            }
            r.setResourceDisplaySummary(summary);
        }
    }

    public int insert(SysAuditEvent row) {
        return mapper.insert(row);
    }
}
