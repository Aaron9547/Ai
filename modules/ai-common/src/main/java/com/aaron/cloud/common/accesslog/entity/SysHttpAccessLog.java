package com.aaron.cloud.common.accesslog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_http_access_log")
public class SysHttpAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String deviceId;
    private String method;
    private String pathPattern;
    private Integer httpStatus;
    private Long durationMs;
    private String traceId;
    private String userAgent;
    private String clientIp;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String tenantCode;

    @TableField(exist = false)
    private String tenantName;

    /** 已登录用户展示名（昵称优先）；非表列 */
    @TableField(exist = false)
    private String userDisplayName;
}
