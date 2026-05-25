package com.aaron.cloud.common.tenant.entity;

import com.aaron.cloud.common.api.enums.tenant.TenantStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;

    /** 管理端侧栏 LOGO（HTTPS URL）；空则前端使用占位样式 */
    private String adminLogoUrl;

    /** 管理端主标题；空则回退 {@link #name} */
    private String adminPortalTitle;

    /** 管理端页脚纯文本；空则前端可隐藏 */
    private String adminFooterText;

    private TenantStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
