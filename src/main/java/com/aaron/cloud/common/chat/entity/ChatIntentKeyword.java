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
    /** 与库列 {@code VARCHAR(128)} 一致，满足 utf8mb4 下 {@code UNIQUE(intent_id, phrase)} 索引长度上限。 */
    private String phrase;
    private ChatIntentKeywordKind keywordKind;
    /** 可选；处理器内轮次名（如 DOC、PLAN），见 {@link ChatIntentKeywordKind} 类注释 */
    private String targetRound;
    private ToggleState enabled;
    private Integer sortOrder;
    /** 配置关键词命中并进入意图 SSE 的累计次数（仅存在 {@code id} 的库行累加）。 */
    private Long hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
