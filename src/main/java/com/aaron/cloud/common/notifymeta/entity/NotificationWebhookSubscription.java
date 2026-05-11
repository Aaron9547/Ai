package com.aaron.cloud.common.notifymeta.entity;

import com.aaron.cloud.common.api.enums.SubscriptionStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notification_webhook_subscription")
public class NotificationWebhookSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String targetUrl;
    private String secretHandle;
    private SubscriptionStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
