package com.aaron.cloud.common.gateway.entity;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("gw_access_party_grant")
public class GwAccessPartyGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accessPartyId;
    private Long endpointId;
    private Long moduleId;
    private Integer grantedRpm;
    private ToggleState enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
