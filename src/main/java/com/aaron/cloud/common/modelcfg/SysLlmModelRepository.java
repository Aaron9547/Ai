package com.aaron.cloud.common.modelcfg;

import com.aaron.cloud.common.api.enums.LlmAnonymousAccess;
import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.enums.LlmModelStatus;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.modelcfg.mapper.SysLlmModelMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysLlmModelRepository {

    private final SysLlmModelMapper mapper;

    public Optional<SysLlmModel> findByTenantAndAlias(long tenantId, String alias) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysLlmModel>lambdaQuery()
                                .eq(SysLlmModel::getTenantId, tenantId)
                                .eq(SysLlmModel::getAlias, alias)
                                .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE)));
    }

    /** C 端对话模型列表：仅启用且类型为 {@link LlmModelKind#LANGUAGE}。 */
    public List<SysLlmModel> listForCatalog(long tenantId, boolean anonymousOnly) {
        var q =
                Wrappers.<SysLlmModel>lambdaQuery()
                        .eq(SysLlmModel::getTenantId, tenantId)
                        .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE)
                        .and(
                                w ->
                                        w.eq(SysLlmModel::getModelKind, LlmModelKind.LANGUAGE)
                                                .or()
                                                .isNull(SysLlmModel::getModelKind))
                        .orderByAsc(SysLlmModel::getSortOrder)
                        .orderByAsc(SysLlmModel::getId);
        if (anonymousOnly) {
            q.eq(SysLlmModel::getAllowAnonymous, LlmAnonymousAccess.ALLOWED);
        }
        return mapper.selectList(q);
    }

    public List<SysLlmModel> listAllForAdmin(long tenantId) {
        return listAllForAdmin(tenantId, null);
    }

    /** @param modelKind 非空时按类型筛选（管理端分 Tab）。 */
    public List<SysLlmModel> listAllForAdmin(long tenantId, LlmModelKind modelKind) {
        var q =
                Wrappers.<SysLlmModel>lambdaQuery()
                        .eq(SysLlmModel::getTenantId, tenantId)
                        .orderByAsc(SysLlmModel::getSortOrder)
                        .orderByAsc(SysLlmModel::getId);
        if (modelKind != null) {
            q.eq(SysLlmModel::getModelKind, modelKind);
        }
        return mapper.selectList(q);
    }

    public Optional<SysLlmModel> findById(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysLlmModel>lambdaQuery()
                                .eq(SysLlmModel::getTenantId, tenantId)
                                .eq(SysLlmModel::getId, id)));
    }

    public int insert(SysLlmModel row) {
        return mapper.insert(row);
    }

    public int updateById(SysLlmModel row) {
        return mapper.updateById(row);
    }

    public int delete(long tenantId, long id) {
        return mapper.delete(
                Wrappers.<SysLlmModel>lambdaQuery()
                        .eq(SysLlmModel::getTenantId, tenantId)
                        .eq(SysLlmModel::getId, id));
    }

    public int addTokensUsedWithinQuota(long tenantId, long modelId, long delta) {
        if (delta <= 0) {
            return 0;
        }
        return mapper.addTokensUsedWithinQuota(tenantId, modelId, delta);
    }

    public int appendTokensUsed(long tenantId, long modelId, long delta) {
        if (delta <= 0) {
            return 0;
        }
        return mapper.appendTokensUsed(tenantId, modelId, delta);
    }

    public boolean existsAlias(long tenantId, String alias, Long excludeId) {
        var q =
                Wrappers.<SysLlmModel>lambdaQuery()
                        .eq(SysLlmModel::getTenantId, tenantId)
                        .eq(SysLlmModel::getAlias, alias);
        if (excludeId != null) {
            q.ne(SysLlmModel::getId, excludeId);
        }
        return mapper.selectCount(q) > 0;
    }

    public long countByTenant(long tenantId) {
        return mapper.selectCount(Wrappers.<SysLlmModel>lambdaQuery().eq(SysLlmModel::getTenantId, tenantId));
    }

    public long countActiveByTenant(long tenantId) {
        return mapper.selectCount(
                Wrappers.<SysLlmModel>lambdaQuery()
                        .eq(SysLlmModel::getTenantId, tenantId)
                        .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE));
    }

    /**
     * 是否存在至少一条启用的联网搜索模型；用于 C 端「联网」开关可用性及发送前校验。
     */
    public boolean hasEnabledWebSearchModel(long tenantId) {
        return mapper.selectCount(
                        Wrappers.<SysLlmModel>lambdaQuery()
                                .eq(SysLlmModel::getTenantId, tenantId)
                                .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE)
                                .eq(SysLlmModel::getModelKind, LlmModelKind.WEB_SEARCH))
                > 0;
    }

    /**
     * 默认联网搜索实例：{@code sort_order} 升序后 {@code id} 升序取第一条（与类注释约定一致，便于管理端通过排序指定默认）。
     */
    public Optional<SysLlmModel> pickDefaultWebSearchModel(long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysLlmModel>lambdaQuery()
                                .eq(SysLlmModel::getTenantId, tenantId)
                                .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE)
                                .eq(SysLlmModel::getModelKind, LlmModelKind.WEB_SEARCH)
                                .orderByAsc(SysLlmModel::getSortOrder)
                                .orderByAsc(SysLlmModel::getId)
                                .last("LIMIT 1")));
    }

    /**
     * 解析租户联网检索模型：优先 {@code configuredId}（须为本租户启用且 {@link LlmModelKind#WEB_SEARCH}），否则 {@link #pickDefaultWebSearchModel}。
     */
    public Optional<SysLlmModel> resolveWebSearchModel(long tenantId, Optional<Long> configuredId) {
        if (configuredId != null && configuredId.isPresent()) {
            Optional<SysLlmModel> bound =
                    findById(tenantId, configuredId.get())
                            .filter(
                                    m ->
                                            m.getModelKind() == LlmModelKind.WEB_SEARCH
                                                    && m.getStatus() == LlmModelStatus.ACTIVE);
            if (bound.isPresent()) {
                return bound;
            }
        }
        return pickDefaultWebSearchModel(tenantId);
    }

    /** 默认对话语言模型：{@code sort_order} 升序后 {@code id} 升序第一条。 */
    public Optional<SysLlmModel> pickDefaultLanguageModel(long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysLlmModel>lambdaQuery()
                                .eq(SysLlmModel::getTenantId, tenantId)
                                .eq(SysLlmModel::getStatus, LlmModelStatus.ACTIVE)
                                .eq(SysLlmModel::getModelKind, LlmModelKind.LANGUAGE)
                                .orderByAsc(SysLlmModel::getSortOrder)
                                .orderByAsc(SysLlmModel::getId)
                                .last("LIMIT 1")));
    }
}
