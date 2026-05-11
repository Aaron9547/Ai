package com.aaron.cloud.common.tenant.runtime.entity;

import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_runtime_setting")
public class TenRuntimeSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private TenantRuntimeSettingKey settingKey;
    private String valueText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
