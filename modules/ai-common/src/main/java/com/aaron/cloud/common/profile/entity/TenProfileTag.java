package com.aaron.cloud.common.profile.entity;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_profile_tag")
public class TenProfileTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    /** {@code u:{userId}} 或 {@code d:{deviceId}} */
    private String subjectKey;

    private ProfileTagCode tagCode;
    private String tagValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
