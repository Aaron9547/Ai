package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ChatUserReminderStatus;
import com.aaron.cloud.common.api.enums.chat.ReminderScheduleType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_user_reminder")
public class ChatUserReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private Long registrationId;
    private Long conversationId;
    private Long intentDefinitionId;
    private String title;
    private String actionText;
    private ReminderScheduleType scheduleType;
    private String cronExpression;
    private LocalDateTime endsAt;
    private ChatUserReminderStatus status;
    private LocalDateTime lastSentAt;
    private Integer sendCount;
    private String mcpRequestJson;
    private String mcpResponseJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
