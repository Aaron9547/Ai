package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.ChatStarterDailyBatchStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 按主体（用户/设备）每日一次的个性化资讯推荐缓存。 */
@Data
@TableName("chat_user_daily_recommend")
public class ChatUserDailyRecommend {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String subjectKey;
    private LocalDate recommendDate;
    private ChatStarterDailyBatchStatus status;
    private String itemsJson;
    private String errorMessage;
    /** 当日失败后是否已用过客户端重试（0/1）。 */
    private Integer retryUsed;
    private LocalDateTime fetchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
