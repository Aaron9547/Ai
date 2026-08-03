package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.chat.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepository {

    private final ChatMessageMapper mapper;

    public Optional<ChatMessage> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .eq(ChatMessage::getId, id)
                                .eq(ChatMessage::getTenantId, tenantId)));
    }

    public int insert(ChatMessage row) {
        return mapper.insert(row);
    }

    public int updateMetaJson(long id, long tenantId, String metaJson) {
        return mapper.update(
                null,
                Wrappers.<ChatMessage>lambdaUpdate()
                        .eq(ChatMessage::getId, id)
                        .eq(ChatMessage::getTenantId, tenantId)
                        .set(ChatMessage::getMetaJson, metaJson));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getId, id)
                        .eq(ChatMessage::getTenantId, tenantId));
    }

    public long countByTenant(long tenantId) {
        return mapper.selectCount(
                Wrappers.<ChatMessage>lambdaQuery().eq(ChatMessage::getTenantId, tenantId));
    }

    /** 按 {@code idsInOrder} 顺序返回消息（用于会话历史展示）。 */
    public List<ChatMessage> listByTenantAndIdsInOrder(long tenantId, List<Long> idsInOrder) {
        if (idsInOrder == null || idsInOrder.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> rows =
                mapper.selectList(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .eq(ChatMessage::getTenantId, tenantId)
                                .in(ChatMessage::getId, idsInOrder));
        Map<Long, ChatMessage> byId = new HashMap<>();
        for (ChatMessage row : rows) {
            byId.put(row.getId(), row);
        }
        List<ChatMessage> ordered = new ArrayList<>(idsInOrder.size());
        for (Long id : idsInOrder) {
            ChatMessage m = byId.get(id);
            if (m != null) {
                ordered.add(m);
            }
        }
        return ordered;
    }
}
