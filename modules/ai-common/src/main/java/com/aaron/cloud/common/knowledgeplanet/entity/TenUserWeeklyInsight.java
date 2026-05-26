package com.aaron.cloud.common.knowledgeplanet.entity;

import com.aaron.cloud.common.api.enums.profile.KnowledgeWeeklyInsightStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_user_weekly_insight")
public class TenUserWeeklyInsight {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private LocalDate weekStart;
    private KnowledgeWeeklyInsightStatus status;
    private String planJson;
    private LocalDateTime computedAt;
    private LocalDateTime emailedAt;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
