package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.mapper.ChatAttachmentMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatAttachmentRepository {

    private final ChatAttachmentMapper mapper;

    public Optional<ChatAttachment> findById(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatAttachment>lambdaQuery()
                                .eq(ChatAttachment::getTenantId, tenantId)
                                .eq(ChatAttachment::getId, id)));
    }

    public List<ChatAttachment> listByIds(long tenantId, long conversationId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<ChatAttachment>lambdaQuery()
                        .eq(ChatAttachment::getTenantId, tenantId)
                        .eq(ChatAttachment::getConversationId, conversationId)
                        .in(ChatAttachment::getId, ids));
    }

    public int insert(ChatAttachment row) {
        return mapper.insert(row);
    }
}
