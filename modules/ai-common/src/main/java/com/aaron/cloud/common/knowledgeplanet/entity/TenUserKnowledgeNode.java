package com.aaron.cloud.common.knowledgeplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_user_knowledge_node")
public class TenUserKnowledgeNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String subjectKey;
    private Long conversationId;
    private Long messageId;
    private String title;
    private String summary;
    /** JSON 数组字符串，如 ["架构","性能"] */
    private String topicTagsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
