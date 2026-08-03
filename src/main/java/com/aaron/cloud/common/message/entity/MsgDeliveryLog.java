package com.aaron.cloud.common.message.entity;

import com.aaron.cloud.common.api.enums.message.MessageChannelType;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("msg_delivery_log")
public class MsgDeliveryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private MessageSceneCode sceneCode;
    private Long channelId;
    private MessageChannelType channelType;
    private String recipient;
    private String requestJson;
    private String providerMsgId;
    private MessageDeliveryStatus status;
    private String errorCode;
    private String errorMessage;
    private Integer attemptCount;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    /** 创建时间 UTC */
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
