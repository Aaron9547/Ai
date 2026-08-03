package com.aaron.cloud.common.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("lnk_chat_conversation_message")
public class LnkChatConversationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long messageId;
    private Integer seq;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
