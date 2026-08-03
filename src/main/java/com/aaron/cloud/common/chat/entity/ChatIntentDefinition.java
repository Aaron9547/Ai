package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_intent_definition")
public class ChatIntentDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String code;
    private String displayName;
    private String description;
    private ChatIntentHandlerKind handlerKind;
    private ToggleState enabled;
    private Integer sortOrder;
    private String extraConfigJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
