package com.aaron.cloud.common.message.entity;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.api.enums.message.MessageChannelType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("msg_channel")
public class MsgChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String channelCode;
    private MessageChannelType channelType;
    private String name;
    private String configJson;
    private String secretJson;
    private MessageChannelStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
