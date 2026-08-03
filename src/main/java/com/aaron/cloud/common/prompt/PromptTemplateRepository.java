package com.aaron.cloud.common.prompt;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import com.aaron.cloud.common.prompt.entity.PromptTemplate;
import com.aaron.cloud.common.prompt.mapper.PromptTemplateMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PromptTemplateRepository {

    private final PromptTemplateMapper mapper;

    public List<PromptTemplate> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getTenantId, tenantId)
                        .orderByAsc(PromptTemplate::getDomain)
                        .orderByAsc(PromptTemplate::getPromptCode)
                        .orderByDesc(PromptTemplate::getVersion));
    }

    public List<PromptTemplate> listByTenantFiltered(
            long tenantId, PromptTemplateDomain domain, PromptTemplateKind kind, String locale, Boolean enabled) {
        var q =
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getTenantId, tenantId)
                        .orderByAsc(PromptTemplate::getDomain)
                        .orderByAsc(PromptTemplate::getPromptCode)
                        .orderByDesc(PromptTemplate::getVersion);
        if (domain != null) {
            q.eq(PromptTemplate::getDomain, domain);
        }
        if (kind != null) {
            q.eq(PromptTemplate::getPromptKind, kind);
        }
        if (locale != null && !locale.isBlank()) {
            q.eq(PromptTemplate::getLocale, locale.trim());
        }
        if (enabled != null) {
            q.eq(PromptTemplate::getEnabled, enabled);
        }
        return mapper.selectList(q);
    }

    /** 管理端列表：平台默认（{@code tenant_id=0}）+ 当前工作区租户覆盖行。 */
    public List<PromptTemplate> listForAdminWorkspace(
            long workspaceTenantId,
            PromptTemplateDomain domain,
            PromptTemplateKind kind,
            String locale,
            Boolean enabled) {
        var q =
                Wrappers.<PromptTemplate>lambdaQuery()
                        .orderByAsc(PromptTemplate::getTenantId)
                        .orderByAsc(PromptTemplate::getDomain)
                        .orderByAsc(PromptTemplate::getPromptCode)
                        .orderByDesc(PromptTemplate::getVersion);
        if (workspaceTenantId == 0L) {
            q.eq(PromptTemplate::getTenantId, 0L);
        } else {
            q.in(PromptTemplate::getTenantId, List.of(0L, workspaceTenantId));
        }
        if (domain != null) {
            q.eq(PromptTemplate::getDomain, domain);
        }
        if (kind != null) {
            q.eq(PromptTemplate::getPromptKind, kind);
        }
        if (locale != null && !locale.isBlank()) {
            q.eq(PromptTemplate::getLocale, locale.trim());
        }
        if (enabled != null) {
            q.eq(PromptTemplate::getEnabled, enabled);
        }
        return mapper.selectList(q);
    }

    public Page<PromptTemplate> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getTenantId, tenantId)
                        .orderByDesc(PromptTemplate::getUpdatedAt));
    }

    public Optional<PromptTemplate> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<PromptTemplate>lambdaQuery()
                                .eq(PromptTemplate::getId, id)
                                .eq(PromptTemplate::getTenantId, tenantId)));
    }

    /** 管理端按主键读取：允许平台行（0）或当前工作区租户行。 */
    public Optional<PromptTemplate> findByIdForAdmin(long id, long workspaceTenantId) {
        PromptTemplate row = mapper.selectById(id);
        if (row == null || row.getTenantId() == null) {
            return Optional.empty();
        }
        long rowTenantId = row.getTenantId();
        if (rowTenantId == 0L || Objects.equals(rowTenantId, workspaceTenantId)) {
            return Optional.of(row);
        }
        return Optional.empty();
    }

    /** 租户 → 平台(0) 回退；locale 精确 → {@code *}；取 enabled 最高 version。 */
    public Optional<PromptTemplate> findActiveForResolve(long tenantId, String promptCode, String locale) {
        String code = promptCode == null ? "" : promptCode.trim();
        if (code.isBlank()) {
            return Optional.empty();
        }
        String loc = normalizeLocale(locale);
        Optional<PromptTemplate> row = findActiveAtTenant(tenantId, code, loc);
        if (row.isPresent()) {
            return row;
        }
        if (tenantId != 0L) {
            row = findActiveAtTenant(0L, code, loc);
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    private Optional<PromptTemplate> findActiveAtTenant(long tenantId, String promptCode, String locale) {
        PromptTemplate exact =
                mapper.selectOne(
                        Wrappers.<PromptTemplate>lambdaQuery()
                                .eq(PromptTemplate::getTenantId, tenantId)
                                .eq(PromptTemplate::getPromptCode, promptCode)
                                .eq(PromptTemplate::getLocale, locale)
                                .eq(PromptTemplate::getEnabled, true)
                                .orderByDesc(PromptTemplate::getVersion)
                                .last("LIMIT 1"));
        if (exact != null) {
            return Optional.of(exact);
        }
        if (!"*".equals(locale)) {
            return Optional.ofNullable(
                    mapper.selectOne(
                            Wrappers.<PromptTemplate>lambdaQuery()
                                    .eq(PromptTemplate::getTenantId, tenantId)
                                    .eq(PromptTemplate::getPromptCode, promptCode)
                                    .eq(PromptTemplate::getLocale, "*")
                                    .eq(PromptTemplate::getEnabled, true)
                                    .orderByDesc(PromptTemplate::getVersion)
                                    .last("LIMIT 1")));
        }
        return Optional.empty();
    }

    public int maxVersion(long tenantId, String promptCode, String locale) {
        String loc = normalizeLocale(locale);
        PromptTemplate row =
                mapper.selectOne(
                        Wrappers.<PromptTemplate>lambdaQuery()
                                .eq(PromptTemplate::getTenantId, tenantId)
                                .eq(PromptTemplate::getPromptCode, promptCode)
                                .eq(PromptTemplate::getLocale, loc)
                                .orderByDesc(PromptTemplate::getVersion)
                                .last("LIMIT 1"));
        return row == null || row.getVersion() == null ? 0 : row.getVersion();
    }

    public int insert(PromptTemplate row) {
        return mapper.insert(row);
    }

    public int updateById(PromptTemplate row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<PromptTemplate>lambdaUpdate()
                        .eq(PromptTemplate::getId, row.getId())
                        .eq(PromptTemplate::getTenantId, tenantId));
    }

    /** 按行上 {@code tenantId} 作用域更新（管理端平台/租户行均可）。 */
    public int updateByIdScoped(PromptTemplate row) {
        return mapper.update(
                row,
                Wrappers.<PromptTemplate>lambdaUpdate()
                        .eq(PromptTemplate::getId, row.getId())
                        .eq(PromptTemplate::getTenantId, row.getTenantId()));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getId, id)
                        .eq(PromptTemplate::getTenantId, tenantId));
    }

    public int deleteByIdScoped(long id, long rowTenantId) {
        return mapper.delete(
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getId, id)
                        .eq(PromptTemplate::getTenantId, rowTenantId));
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh-CN";
        }
        return locale.trim();
    }
}
