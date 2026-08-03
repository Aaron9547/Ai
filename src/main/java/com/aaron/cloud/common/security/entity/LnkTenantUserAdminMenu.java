package com.aaron.cloud.common.security.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("lnk_tenant_user_admin_menu")
public class LnkTenantUserAdminMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String menuCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
