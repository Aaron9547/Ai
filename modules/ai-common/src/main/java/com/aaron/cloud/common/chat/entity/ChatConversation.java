package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ConversationRecordStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String deviceId;
    private String title;
    private ConversationRecordStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 管理端列表：{@code sys_tenant.code}，非表列。 */
    @TableField(exist = false)
    private String tenantCode;

    @TableField(exist = false)
    private String tenantName;

    @TableField(exist = false)
    private String userDisplayName;

    /**
     * 管理端列表：本会话内助手消息（含历史稿）meta 中 token 之和的近似值，非表列；用于运营抽检成本，非计费真源。
     */
    @TableField(exist = false)
    private Integer totalTokensInConversation;
}
