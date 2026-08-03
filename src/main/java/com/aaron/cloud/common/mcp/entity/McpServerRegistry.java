package com.aaron.cloud.common.mcp.entity;

import com.aaron.cloud.common.api.enums.mcp.McpServerStatus;
import com.aaron.cloud.common.api.enums.mcp.McpTransportKind;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mcp_server_registry")
public class McpServerRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;
    private String baseUrl;
    private McpTransportKind transportKind;
    /** AES-GCM 密文：出站 HTTP 头 JSON，如 {@code {"Authorization":"Bearer jina_xxx"}} */
    private String authHeadersCipher;
    private String description;
    private McpServerStatus status;
    private LocalDateTime lastProbeAt;
    /** 0=失败 1=成功 */
    private Integer lastProbeOk;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
