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
@TableName("gw_access_party")
public class GwAccessParty {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String appId;
    private String secretCipher;
    private String displayName;
    private ToggleState status;
    private Integer totalRpmCap;
    private String remark;
    private LocalDateTime lastRotatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
