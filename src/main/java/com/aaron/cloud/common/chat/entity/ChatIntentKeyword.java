package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_intent_keyword")
public class ChatIntentKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long intentId;
    private String phrase;
    private ChatIntentKeywordKind keywordKind;
    private ToggleState enabled;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
