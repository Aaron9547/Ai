package com.aaron.cloud.common.platform.entity;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_platform_setting")
public class SysPlatformSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private PlatformSettingKey settingKey;
    private String valueText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
