package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatUserDailyRecommend;
import com.aaron.cloud.common.chat.mapper.ChatUserDailyRecommendMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatUserDailyRecommendRepository {

    private final ChatUserDailyRecommendMapper mapper;

    public Optional<ChatUserDailyRecommend> findByTenantSubjectAndDate(
            long tenantId, String subjectKey, LocalDate recommendDate) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatUserDailyRecommend>lambdaQuery()
                                .eq(ChatUserDailyRecommend::getTenantId, tenantId)
                                .eq(ChatUserDailyRecommend::getSubjectKey, subjectKey)
                                .eq(ChatUserDailyRecommend::getRecommendDate, recommendDate)));
    }

    public int insert(ChatUserDailyRecommend row) {
        return mapper.insert(row);
    }

    public int updateById(ChatUserDailyRecommend row) {
        return mapper.updateById(row);
    }

    public int deleteByTenantSubjectAndDate(long tenantId, String subjectKey, LocalDate recommendDate) {
        return mapper.delete(
                Wrappers.<ChatUserDailyRecommend>lambdaQuery()
                        .eq(ChatUserDailyRecommend::getTenantId, tenantId)
                        .eq(ChatUserDailyRecommend::getSubjectKey, subjectKey)
                        .eq(ChatUserDailyRecommend::getRecommendDate, recommendDate));
    }
}
