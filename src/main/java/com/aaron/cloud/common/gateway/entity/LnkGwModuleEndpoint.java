package com.aaron.cloud.common.gateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("lnk_gw_module_endpoint")
public class LnkGwModuleEndpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    private Long endpointId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
