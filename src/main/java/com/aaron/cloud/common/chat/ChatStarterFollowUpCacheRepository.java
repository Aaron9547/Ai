package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatStarterFollowUpCache;
import com.aaron.cloud.common.chat.mapper.ChatStarterFollowUpCacheMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * 按租户 + 助手消息 id 幂等写入追问缓存（SSE 后台与 REST 拉取可能并发）。
     */
    public void saveQuestions(long tenantId, long assistantMessageId, String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return;
        }
        Optional<ChatStarterFollowUpCache> existing =
                findByAssistantMessage(tenantId, assistantMessageId);
        if (existing.isPresent()) {
            ChatStarterFollowUpCache row = existing.get();
            row.setQuestionsJson(questionsJson);
            mapper.updateById(row);
            return;
        }
        var row = new ChatStarterFollowUpCache();
        row.setTenantId(tenantId);
        row.setAssistantMessageId(assistantMessageId);
        row.setQuestionsJson(questionsJson);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            findByAssistantMessage(tenantId, assistantMessageId)
                    .ifPresent(
                            cached -> {
                                cached.setQuestionsJson(questionsJson);
                                mapper.updateById(cached);
                            });
        }
    }

    public int deleteByAssistantMessage(long tenantId, long assistantMessageId) {
        return mapper.delete(
                Wrappers.<ChatStarterFollowUpCache>lambdaQuery()
                        .eq(ChatStarterFollowUpCache::getTenantId, tenantId)
                        .eq(ChatStarterFollowUpCache::getAssistantMessageId, assistantMessageId));
    }
}
