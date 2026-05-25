package com.aaron.cloud.common.metering.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("metering_usage_event")
public class MeteringUsageEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String deviceId;
    private String meterType;
    private BigDecimal quantity;
    private String unit;
    private String refJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 管理端列表展示用，非表列；由仓储按 tenantId 批量填充 {@code sys_tenant.code}。 */
    @TableField(exist = false)
    private String tenantCode;

    @TableField(exist = false)
    private String tenantName;

    @TableField(exist = false)
    private String userDisplayName;
}
