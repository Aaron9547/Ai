package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.chat.mapper.ChatStarterPromptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatStarterPromptRepository {

    private final ChatStarterPromptMapper mapper;

    public Optional<ChatStarterPrompt> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatStarterPrompt>lambdaQuery()
                                .eq(ChatStarterPrompt::getId, id)
                                .eq(ChatStarterPrompt::getTenantId, tenantId)));
    }

    public List<ChatStarterPrompt> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .orderByDesc(ChatStarterPrompt::getSortOrder)
                        .orderByDesc(ChatStarterPrompt::getWeight)
                        .orderByAsc(ChatStarterPrompt::getId));
    }

    /** 管理端：按场景（及可选来源）分页列表；按 {@code updated_at} 从新到旧。 */
    public Page<ChatStarterPrompt> pageByTenant(
            long tenantId,
            ChatStarterPromptScene scene,
            ChatStarterPromptSource source,
            long pageNo,
            long pageSize) {
        var q =
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .eq(ChatStarterPrompt::getScene, scene)
                        .orderByDesc(ChatStarterPrompt::getUpdatedAt)
                        .orderByDesc(ChatStarterPrompt::getId);
        if (source != null) {
            q.eq(ChatStarterPrompt::getSource, source);
        }
        return mapper.selectPage(new Page<>(pageNo, pageSize), q);
    }

    public List<ChatStarterPrompt> listEnabledForRuntime(
            long tenantId, ChatStarterPromptScene scene, LocalDate today) {
        return mapper.selectList(
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .eq(ChatStarterPrompt::getScene, scene)
                        .eq(ChatStarterPrompt::getEnabled, 1)
                        .and(
                                w ->
                                        w.isNull(ChatStarterPrompt::getValidFrom)
                                                .or()
                                                .le(ChatStarterPrompt::getValidFrom, today))
                        .and(
                                w ->
                                        w.isNull(ChatStarterPrompt::getValidUntil)
                                                .or()
                                                .ge(ChatStarterPrompt::getValidUntil, today))
                        .orderByDesc(ChatStarterPrompt::getWeight)
                        .orderByDesc(ChatStarterPrompt::getSortOrder)
                        .orderByAsc(ChatStarterPrompt::getId));
    }

    public int deleteHotTopicByTenantExceptBatch(long tenantId, String keepBatchKey) {
        return mapper.delete(
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .eq(ChatStarterPrompt::getSource, ChatStarterPromptSource.HOT_TOPIC_DAILY)
                        .ne(ChatStarterPrompt::getBatchKey, keepBatchKey));
    }

    public int insert(ChatStarterPrompt row) {
        return mapper.insert(row);
    }

    public int updateById(ChatStarterPrompt row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<ChatStarterPrompt>lambdaUpdate()
                        .eq(ChatStarterPrompt::getId, row.getId())
                        .eq(ChatStarterPrompt::getTenantId, tenantId));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getId, id)
                        .eq(ChatStarterPrompt::getTenantId, tenantId));
    }

    /** 取该问句最新一条联网知识（按 {@code updated_at} 降序，允许多版本并存）。 */
    public Optional<ChatStarterPrompt> findLatestWebKnowledgeByHash(
            long tenantId, String queryNormHash, boolean enabledOnly) {
        if (queryNormHash == null || queryNormHash.isBlank()) {
            return Optional.empty();
        }
        var q =
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .eq(ChatStarterPrompt::getScene, ChatStarterPromptScene.WEB_KNOWLEDGE)
                        .eq(
                                ChatStarterPrompt::getSource,
                                ChatStarterPromptSource.WEB_SEARCH_GROUNDING)
                        .eq(ChatStarterPrompt::getQueryNormHash, queryNormHash)
                        .orderByDesc(ChatStarterPrompt::getUpdatedAt)
                        .orderByDesc(ChatStarterPrompt::getId);
        if (enabledOnly) {
            q.eq(ChatStarterPrompt::getEnabled, 1);
        }
        return Optional.ofNullable(mapper.selectOne(q.last("LIMIT 1")));
    }

    public List<ChatStarterPrompt> listWebKnowledgeForSemanticScan(long tenantId, int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        return mapper.selectList(
                Wrappers.<ChatStarterPrompt>lambdaQuery()
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .eq(ChatStarterPrompt::getScene, ChatStarterPromptScene.WEB_KNOWLEDGE)
                        .eq(
                                ChatStarterPrompt::getSource,
                                ChatStarterPromptSource.WEB_SEARCH_GROUNDING)
                        .eq(ChatStarterPrompt::getEnabled, 1)
                        .isNotNull(ChatStarterPrompt::getGroundingJson)
                        .orderByDesc(ChatStarterPrompt::getUpdatedAt)
                        .last("LIMIT " + cap));
    }

    public int incrementHitCount(long id, long tenantId) {
        return mapper.update(
                null,
                Wrappers.<ChatStarterPrompt>lambdaUpdate()
                        .eq(ChatStarterPrompt::getId, id)
                        .eq(ChatStarterPrompt::getTenantId, tenantId)
                        .setSql("hit_count = hit_count + 1"));
    }
}
