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

    /** 规范化问句，用于展示与语义匹配。 */
    private String queryNormalized;

    /** {@code SHA256(规范化问句)} 十六进制，用于索引与多版本分组（避免 utf8mb4 长字段唯一索引）。 */
    private String queryNormHash;

    /** 联网检索摘要与引用 JSON（scene=WEB_KNOWLEDGE 时使用）。 */
    private String groundingJson;

    /** 本地知识库命中次数（外呼前命中时递增）。 */
    private Integer hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
