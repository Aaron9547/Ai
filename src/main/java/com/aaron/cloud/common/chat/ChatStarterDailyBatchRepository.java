package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatStarterDailyBatch;
import com.aaron.cloud.common.chat.mapper.ChatStarterDailyBatchMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatStarterDailyBatchRepository {

    private final ChatStarterDailyBatchMapper mapper;

    public Optional<ChatStarterDailyBatch> findByTenantAndDate(long tenantId, LocalDate topicDate) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatStarterDailyBatch>lambdaQuery()
                                .eq(ChatStarterDailyBatch::getTenantId, tenantId)
                                .eq(ChatStarterDailyBatch::getTopicDate, topicDate)));
    }

    public List<ChatStarterDailyBatch> listByTenant(long tenantId, int limit) {
        return mapper.selectList(
                Wrappers.<ChatStarterDailyBatch>lambdaQuery()
                        .eq(ChatStarterDailyBatch::getTenantId, tenantId)
                        .orderByDesc(ChatStarterDailyBatch::getTopicDate)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 90))));
    }

    public int insert(ChatStarterDailyBatch row) {
        return mapper.insert(row);
    }

    public int updateById(ChatStarterDailyBatch row) {
        return mapper.updateById(row);
    }
}
