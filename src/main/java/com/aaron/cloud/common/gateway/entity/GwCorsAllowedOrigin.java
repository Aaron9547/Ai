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
@TableName("gw_cors_allowed_origin")
public class GwCorsAllowedOrigin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 完整 Origin（scheme + host [+ port]，无路径片段）。 */
    private String origin;

    private ToggleState enabled;
    private Integer sortOrder;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
