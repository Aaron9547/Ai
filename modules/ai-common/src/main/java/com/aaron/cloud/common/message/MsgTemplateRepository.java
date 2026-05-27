package com.aaron.cloud.common.message;

import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;
import com.aaron.cloud.common.message.entity.MsgTemplate;
import com.aaron.cloud.common.message.mapper.MsgTemplateMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MsgTemplateRepository {

    private final MsgTemplateMapper mapper;

    public List<MsgTemplate> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getTenantId, tenantId)
                        .orderByAsc(MsgTemplate::getSceneCode));
    }

    public Page<MsgTemplate> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getTenantId, tenantId)
                        .orderByDesc(MsgTemplate::getUpdatedAt));
    }

    public Optional<MsgTemplate> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgTemplate>lambdaQuery()
                                .eq(MsgTemplate::getId, id)
                                .eq(MsgTemplate::getTenantId, tenantId)));
    }

    public Optional<MsgTemplate> findActiveByScene(long tenantId, MessageSceneCode scene, String locale) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
        MsgTemplate exact =
                mapper.selectOne(
                        Wrappers.<MsgTemplate>lambdaQuery()
                                .eq(MsgTemplate::getTenantId, tenantId)
                                .eq(MsgTemplate::getSceneCode, scene)
                                .eq(MsgTemplate::getLocale, loc)
                                .eq(MsgTemplate::getStatus, MessageTemplateStatus.ACTIVE)
                                .last("LIMIT 1"));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgTemplate>lambdaQuery()
                                .eq(MsgTemplate::getTenantId, tenantId)
                                .eq(MsgTemplate::getSceneCode, scene)
                                .eq(MsgTemplate::getLocale, "zh-CN")
                                .eq(MsgTemplate::getStatus, MessageTemplateStatus.ACTIVE)
                                .last("LIMIT 1")));
    }

    public boolean existsScene(long tenantId, MessageSceneCode scene, String locale, Long excludeId) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
        var q =
                Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getTenantId, tenantId)
                        .eq(MsgTemplate::getSceneCode, scene)
                        .eq(MsgTemplate::getLocale, loc);
        if (excludeId != null) {
            q.ne(MsgTemplate::getId, excludeId);
        }
        return mapper.selectCount(q) > 0;
    }

    public int insert(MsgTemplate row) {
        return mapper.insert(row);
    }

    public int updateById(MsgTemplate row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<MsgTemplate>lambdaUpdate()
                        .eq(MsgTemplate::getId, row.getId())
                        .eq(MsgTemplate::getTenantId, tenantId));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<MsgTemplate>lambdaQuery()
                        .eq(MsgTemplate::getId, id)
                        .eq(MsgTemplate::getTenantId, tenantId));
    }
}
