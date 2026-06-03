package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.chat.ChatUserReminderStatus;
import com.aaron.cloud.common.chat.entity.ChatUserReminder;
import com.aaron.cloud.common.chat.mapper.ChatUserReminderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatUserReminderRepository {

    private final ChatUserReminderMapper mapper;

    public List<ChatUserReminder> listActiveByUser(long tenantId, long userId) {
        return mapper.selectList(
                Wrappers.<ChatUserReminder>lambdaQuery()
                        .eq(ChatUserReminder::getTenantId, tenantId)
                        .eq(ChatUserReminder::getUserId, userId)
                        .eq(ChatUserReminder::getStatus, ChatUserReminderStatus.ACTIVE)
                        .orderByDesc(ChatUserReminder::getUpdatedAt));
    }

    public int countActiveByUser(long tenantId, long userId) {
        Long n =
                mapper.selectCount(
                        Wrappers.<ChatUserReminder>lambdaQuery()
                                .eq(ChatUserReminder::getTenantId, tenantId)
                                .eq(ChatUserReminder::getUserId, userId)
                                .eq(ChatUserReminder::getStatus, ChatUserReminderStatus.ACTIVE));
        return n == null ? 0 : n.intValue();
    }

    public Optional<ChatUserReminder> findByRegistrationId(long tenantId, long registrationId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatUserReminder>lambdaQuery()
                                .eq(ChatUserReminder::getTenantId, tenantId)
                                .eq(ChatUserReminder::getRegistrationId, registrationId)));
    }

    public Optional<ChatUserReminder> findById(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatUserReminder>lambdaQuery()
                                .eq(ChatUserReminder::getTenantId, tenantId)
                                .eq(ChatUserReminder::getId, id)));
    }

    public int insert(ChatUserReminder row) {
        return mapper.insert(row);
    }

    public int updateById(ChatUserReminder row) {
        return mapper.updateById(row);
    }
}
