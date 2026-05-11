package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.LnkChatConversationMessage;
import com.aaron.cloud.common.chat.mapper.LnkChatConversationMessageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LnkChatConversationMessageRepository {

    private final LnkChatConversationMessageMapper mapper;

    public int insert(LnkChatConversationMessage row) {
        return mapper.insert(row);
    }

    public List<Long> listMessageIdsByConversationOrderByLinkIdAsc(long conversationId) {
        return mapper.selectList(
                        Wrappers.<LnkChatConversationMessage>lambdaQuery()
                                .eq(LnkChatConversationMessage::getConversationId, conversationId)
                                .orderByAsc(LnkChatConversationMessage::getId))
                .stream()
                .map(LnkChatConversationMessage::getMessageId)
                .toList();
    }

    public boolean existsMessageInConversation(long conversationId, long messageId) {
        return mapper.selectCount(
                        Wrappers.<LnkChatConversationMessage>lambdaQuery()
                                .eq(LnkChatConversationMessage::getConversationId, conversationId)
                                .eq(LnkChatConversationMessage::getMessageId, messageId))
                > 0;
    }

    public int deleteByConversationIdAndMessageId(long conversationId, long messageId) {
        return mapper.delete(
                Wrappers.<LnkChatConversationMessage>lambdaQuery()
                        .eq(LnkChatConversationMessage::getConversationId, conversationId)
                        .eq(LnkChatConversationMessage::getMessageId, messageId));
    }
}
