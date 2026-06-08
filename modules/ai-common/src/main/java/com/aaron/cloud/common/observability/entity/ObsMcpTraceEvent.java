package com.aaron.cloud.common.observability.entity;

import com.aaron.cloud.common.api.enums.observability.McpTraceSourceScene;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("obs_mcp_trace_event")
public class ObsMcpTraceEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private String orchestrationTraceId;
    private String httpTraceId;
    private Long tenantId;
    private Long userId;
    private Long conversationId;
    private String conversationPublicId;
    private McpTraceSourceScene sourceScene;
    private Integer llmRound;
    private String qualifiedToolName;
    private Long serverId;
    private Boolean success;
    private String errorCode;
    private Long latencyMs;
    private String argumentsJson;
    private String resultJson;
    private LocalDateTime createdAt;

    /** 管理端展示：会话标题，非表列。 */
    @TableField(exist = false)
    private String conversationTitle;
}
