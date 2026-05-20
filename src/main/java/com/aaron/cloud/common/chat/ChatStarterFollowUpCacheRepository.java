package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatStarterFollowUpCache;
import com.aaron.cloud.common.chat.mapper.ChatStarterFollowUpCacheMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatStarterFollowUpCacheRepository {

    private final ChatStarterFollowUpCacheMapper mapper;

    public Optional<ChatStarterFollowUpCache> findByAssistantMessage(long tenantId, long assistantMessageId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatStarterFollowUpCache>lambdaQuery()
                                .eq(ChatStarterFollowUpCache::getTenantId, tenantId)
                                .eq(ChatStarterFollowUpCache::getAssistantMessageId, assistantMessageId)));
    }

    public int insert(ChatStarterFollowUpCache row) {
        return mapper.insert(row);
    }

    public int deleteByAssistantMessage(long tenantId, long assistantMessageId) {
        return mapper.delete(
                Wrappers.<ChatStarterFollowUpCache>lambdaQuery()
                        .eq(ChatStarterFollowUpCache::getTenantId, tenantId)
                        .eq(ChatStarterFollowUpCache::getAssistantMessageId, assistantMessageId));
    }
}
