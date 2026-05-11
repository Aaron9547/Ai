package com.aaron.cloud.common.gateway.entity;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("gw_api_endpoint")
public class GwApiEndpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Ant 风格路径，与 {@link GwApiRateLimitRule#getPathPattern()} 一致。 */
    private String pathPattern;

    /** {@code *} 或具体 HTTP 方法大写。 */
    private String httpMethod;

    private String displayName;
    private String remark;
    private ToggleState enabled;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
