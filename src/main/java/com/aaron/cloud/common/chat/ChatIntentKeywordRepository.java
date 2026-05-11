package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.chat.mapper.ChatIntentKeywordMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatIntentKeywordRepository {

    private final ChatIntentKeywordMapper mapper;

    public Optional<ChatIntentKeyword> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatIntentKeyword>lambdaQuery()
                                .eq(ChatIntentKeyword::getId, id)
                                .eq(ChatIntentKeyword::getTenantId, tenantId)));
    }

    public List<ChatIntentKeyword> listByIntent(long tenantId, long intentId) {
        return mapper.selectList(
                Wrappers.<ChatIntentKeyword>lambdaQuery()
                        .eq(ChatIntentKeyword::getTenantId, tenantId)
                        .eq(ChatIntentKeyword::getIntentId, intentId)
                        .orderByAsc(ChatIntentKeyword::getSortOrder)
                        .orderByAsc(ChatIntentKeyword::getId));
    }

    public List<ChatIntentKeyword> listEnabledByIntentAndKind(
            long tenantId, long intentId, ChatIntentKeywordKind kind) {
        return mapper.selectList(
                Wrappers.<ChatIntentKeyword>lambdaQuery()
                        .eq(ChatIntentKeyword::getTenantId, tenantId)
                        .eq(ChatIntentKeyword::getIntentId, intentId)
                        .eq(ChatIntentKeyword::getKeywordKind, kind)
                        .eq(ChatIntentKeyword::getEnabled, ToggleState.ON)
                        .orderByDesc(ChatIntentKeyword::getSortOrder)
                        .orderByAsc(ChatIntentKeyword::getId));
    }

    public int insert(ChatIntentKeyword row) {
        return mapper.insert(row);
    }

    public int updateById(ChatIntentKeyword row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<ChatIntentKeyword>lambdaUpdate()
                        .eq(ChatIntentKeyword::getId, row.getId())
                        .eq(ChatIntentKeyword::getTenantId, tenantId));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<ChatIntentKeyword>lambdaQuery()
                        .eq(ChatIntentKeyword::getId, id)
                        .eq(ChatIntentKeyword::getTenantId, tenantId));
    }

    public int deleteByIntent(long tenantId, long intentId) {
        return mapper.delete(
                Wrappers.<ChatIntentKeyword>lambdaQuery()
                        .eq(ChatIntentKeyword::getTenantId, tenantId)
                        .eq(ChatIntentKeyword::getIntentId, intentId));
    }
}
