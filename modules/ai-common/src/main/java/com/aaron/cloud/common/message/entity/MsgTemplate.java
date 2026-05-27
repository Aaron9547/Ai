package com.aaron.cloud.common.message.entity;

import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("msg_template")
public class MsgTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private MessageSceneCode sceneCode;
    private Long channelId;
    private String subjectTemplate;
    private String bodyTemplate;
    private String locale;
    private MessageTemplateStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
