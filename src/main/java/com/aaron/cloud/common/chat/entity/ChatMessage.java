package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private ChatMessageRole role;
    private String content;
    private String metaJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
