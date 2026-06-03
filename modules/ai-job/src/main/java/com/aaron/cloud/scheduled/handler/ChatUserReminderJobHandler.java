package com.aaron.cloud.scheduled.handler;

import com.aaron.cloud.chat.reminder.ChatUserReminderEmailDispatchService;
import com.aaron.cloud.common.api.enums.chat.ChatUserReminderStatus;
import com.aaron.cloud.common.api.enums.chat.ReminderScheduleType;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.chat.ChatUserReminderRepository;
import com.aaron.cloud.common.chat.entity.ChatUserReminder;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.scheduled.TenantScheduledJobHandler;
import com.aaron.cloud.scheduled.run.TenantScheduledRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUserReminderJobHandler implements TenantScheduledJobHandler {

    private final ChatUserReminderRepository reminderRepository;
    private final TenantScheduledTaskRepository scheduledTaskRepository;
    private final ChatUserReminderEmailDispatchService emailDispatchService;

    @Override
    public TenantScheduledExecutorCode executorCode() {
        return TenantScheduledExecutorCode.CHAT_USER_REMINDER;
    }

    @Override
    public void execute(TenantScheduledTask registration, TenantScheduledRunContext runContext) throws Exception {
        long tenantId = registration.getTenantId();
        var reminderOpt = reminderRepository.findByRegistrationId(tenantId, registration.getId());
        if (reminderOpt.isEmpty()) {
            runContext.report("SKIPPED", "无关联提醒记录", 100, null, null);
            registration.setEnabled(0);
            scheduledTaskRepository.updateById(registration);
            return;
        }
        ChatUserReminder reminder = reminderOpt.get();
        if (reminder.getStatus() != ChatUserReminderStatus.ACTIVE) {
            runContext.report("SKIPPED", "提醒非 ACTIVE", 100, null, null);
            return;
        }
        if (registration.getEnabled() == null || registration.getEnabled() != 1) {
            runContext.report("SKIPPED", "定时任务已关闭", 100, null, null);
            return;
        }
        var now = BeijingTime.nowLocal();
        if (reminder.getEndsAt() != null && reminder.getEndsAt().isBefore(now)) {
            reminder.setStatus(ChatUserReminderStatus.EXPIRED);
            reminderRepository.updateById(reminder);
            registration.setEnabled(0);
            scheduledTaskRepository.updateById(registration);
            runContext.report("SKIPPED", "提醒已过期", 100, null, null);
            return;
        }
        runContext.report("EMAIL", "发送提醒邮件", 40, null, null);
        boolean sent = emailDispatchService.sendReminderEmail(reminder, now);
        if (!sent) {
            runContext.report("SKIPPED", "邮件未发送（场景未就绪或无邮箱）", 100, null, null);
            return;
        }
        reminder.setLastSentAt(now);
        reminder.setSendCount(reminder.getSendCount() == null ? 1 : reminder.getSendCount() + 1);
        if (reminder.getScheduleType() == ReminderScheduleType.ONCE) {
            reminder.setStatus(ChatUserReminderStatus.EXPIRED);
            registration.setEnabled(0);
            scheduledTaskRepository.updateById(registration);
        }
        reminderRepository.updateById(reminder);
        runContext.report("DONE", "邮件已提交发送", 100, null, null);
        log.info(
                "[提醒邮件] 执行完成 tenantId={} reminderId={} registrationId={}",
                tenantId,
                reminder.getId(),
                registration.getId());
    }
}
