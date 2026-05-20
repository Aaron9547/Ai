package com.aaron.cloud.common.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_starter_follow_up_cache")
public class ChatStarterFollowUpCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long assistantMessageId;
    private String questionsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
