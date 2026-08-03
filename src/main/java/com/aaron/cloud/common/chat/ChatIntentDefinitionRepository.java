package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.mapper.ChatIntentDefinitionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatIntentDefinitionRepository {

    private final ChatIntentDefinitionMapper mapper;

    public Optional<ChatIntentDefinition> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatIntentDefinition>lambdaQuery()
                                .eq(ChatIntentDefinition::getId, id)
                                .eq(ChatIntentDefinition::getTenantId, tenantId)));
    }

    public Optional<ChatIntentDefinition> findByTenantAndCode(long tenantId, String code) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatIntentDefinition>lambdaQuery()
                                .eq(ChatIntentDefinition::getTenantId, tenantId)
                                .eq(ChatIntentDefinition::getCode, code)));
    }

    public List<ChatIntentDefinition> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<ChatIntentDefinition>lambdaQuery()
                        .eq(ChatIntentDefinition::getTenantId, tenantId)
                        .orderByDesc(ChatIntentDefinition::getSortOrder)
                        .orderByAsc(ChatIntentDefinition::getId));
    }

    /** 对话侧：仅启用意图，按 sort_order 降序（大者优先匹配）。 */
    public List<ChatIntentDefinition> listEnabledForRuntime(long tenantId) {
        return mapper.selectList(
                Wrappers.<ChatIntentDefinition>lambdaQuery()
                        .eq(ChatIntentDefinition::getTenantId, tenantId)
                        .eq(ChatIntentDefinition::getEnabled, ToggleState.ON)
                        .orderByDesc(ChatIntentDefinition::getSortOrder)
                        .orderByAsc(ChatIntentDefinition::getId));
    }

    public int insert(ChatIntentDefinition row) {
        return mapper.insert(row);
    }

    public int updateById(ChatIntentDefinition row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<ChatIntentDefinition>lambdaUpdate()
                        .eq(ChatIntentDefinition::getId, row.getId())
                        .eq(ChatIntentDefinition::getTenantId, tenantId));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<ChatIntentDefinition>lambdaQuery()
                        .eq(ChatIntentDefinition::getId, id)
                        .eq(ChatIntentDefinition::getTenantId, tenantId));
    }
}
