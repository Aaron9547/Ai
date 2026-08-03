package com.aaron.cloud.common.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_conversation_share")
public class ChatConversationShare {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long conversationId;
    private String shareCode;
    private String title;
    private String snapshotJson;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
