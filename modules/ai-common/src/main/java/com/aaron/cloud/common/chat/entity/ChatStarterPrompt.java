package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_starter_prompt")
public class ChatStarterPrompt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private ChatStarterPromptScene scene;
    private ChatStarterPromptSource source;
    private String promptText;
    private Integer weight;
    private Integer enabled;
    private Integer requireThinking;
    private Integer requireWebSearch;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Integer sortOrder;
    private String batchKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
