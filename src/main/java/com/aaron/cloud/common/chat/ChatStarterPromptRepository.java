package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.ChatStarterPromptSource;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.chat.mapper.ChatStarterPromptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
}
