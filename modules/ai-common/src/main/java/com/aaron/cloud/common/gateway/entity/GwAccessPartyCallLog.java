package com.aaron.cloud.common.gateway.entity;

import com.aaron.cloud.common.api.enums.gateway.GwApiInterfaceKind;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("gw_access_party_call_log")
public class GwAccessPartyCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long accessPartyId;
    private Long endpointId;
    private String method;
    private String pathPattern;
    private Integer httpStatus;
    private Long durationMs;
    private String clientIp;
    private Long tokensConsumed;
    private GwApiInterfaceKind interfaceKind;
    private String errorCode;
    private String traceId;
    private LocalDateTime createdAt;
}
