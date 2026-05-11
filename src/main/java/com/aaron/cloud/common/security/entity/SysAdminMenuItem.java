package com.aaron.cloud.common.security.entity;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_admin_menu_item")
public class SysAdminMenuItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String menuCode;
    private String titleZh;
    private String routePath;
    private Integer sortOrder;
    private ToggleState enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
