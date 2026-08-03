package com.aaron.cloud.chat.reminder;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.enums.chat.ReminderScheduleType;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.ports.MessageSendPort;
import com.aaron.cloud.common.chat.entity.ChatUserReminder;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUserReminderEmailDispatchService {

    private static final DateTimeFormatter IDEM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final MessageSceneReadinessQuery sceneReadinessQuery;
    private final MessageSendPort messageSendPort;
    private final SecUserAccountRepository userAccountRepository;

    public boolean sendReminderEmail(ChatUserReminder reminder, java.time.LocalDateTime now) {
        long tenantId = reminder.getTenantId();
        if (!sceneReadinessQuery.isSceneConfigured(tenantId, MessageSceneCode.CHAT_USER_REMINDER)) {
            log.info("[提醒邮件] 场景未配置 tenantId={} reminderId={}", tenantId, reminder.getId());
            return false;
        }
        SecUserAccount user =
                userAccountRepository.findById(reminder.getUserId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("[提醒邮件] 用户无邮箱 userId={} reminderId={}", reminder.getUserId(), reminder.getId());
            return false;
        }
        String idem =
                "reminder:"
                        + reminder.getId()
                        + ":"
                        + now.format(IDEM_FMT);
        var result =
                messageSendPort.send(
                        MessageSendRequest.builder()
                                .tenantId(tenantId)
                                .sceneCode(MessageSceneCode.CHAT_USER_REMINDER)
                                .recipient(user.getEmail().trim())
                                .templateVars(
                                        Map.of(
                                                "title",
                                                reminder.getTitle() == null ? "" : reminder.getTitle(),
                                                "actionText",
                                                reminder.getActionText() == null
                                                        ? ""
                                                        : reminder.getActionText(),
                                                "scheduleType",
                                                scheduleLabel(reminder.getScheduleType())))
                                .idempotencyKey(idem)
                                .async(true)
                                .build());
        if (result.getStatus() == MessageDeliveryStatus.FAILED) {
            log.warn(
                    "[提醒邮件] 发送失败 reminderId={} err={}",
                    reminder.getId(),
                    result.getErrorMessage());
            return false;
        }
        return true;
    }

    private static String scheduleLabel(ReminderScheduleType type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case ONCE -> "单次";
            case DAILY -> "每天";
            case WEEKLY -> "每周";
            case MONTHLY -> "每月";
        };
    }
}
