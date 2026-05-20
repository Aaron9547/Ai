package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.ChatStarterEventType;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_starter_event")
public class ChatStarterEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String deviceId;
    private Long promptId;
    private ChatStarterPromptScene scene;
    private ChatStarterEventType eventType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
