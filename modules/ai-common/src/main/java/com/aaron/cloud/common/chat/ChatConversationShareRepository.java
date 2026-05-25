package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatConversationShare;
import com.aaron.cloud.common.chat.mapper.ChatConversationShareMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatConversationShareRepository {

    private final ChatConversationShareMapper mapper;

    public int insert(ChatConversationShare row) {
        return mapper.insert(row);
    }

    public Optional<ChatConversationShare> findByCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatConversationShare>lambdaQuery()
                                .eq(ChatConversationShare::getShareCode, shareCode.trim())));
    }

    public Optional<ChatConversationShare> findActiveByCode(long tenantId, String shareCode) {
        return findByCode(shareCode)
                .filter(row -> row.getTenantId() != null && row.getTenantId().equals(tenantId))
                .filter(
                        row ->
                                row.getExpiresAt() == null
                                        || !row.getExpiresAt().isBefore(LocalDateTime.now()));
    }
}
