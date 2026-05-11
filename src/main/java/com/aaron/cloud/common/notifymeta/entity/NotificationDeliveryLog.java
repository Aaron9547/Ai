package com.aaron.cloud.common.notifymeta.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notification_delivery_log")
public class NotificationDeliveryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long subscriptionId;
    private Integer httpStatus;
    private String resultJson;
    private LocalDateTime createdAt;
}
