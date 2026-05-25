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
@TableName("gw_api_module")
public class GwApiModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String displayName;
    private Integer sortOrder;
    private ToggleState enabled;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
