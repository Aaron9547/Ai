package com.aaron.cloud.common.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_audit_event")
public class SysAuditEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String actorType;
    private String actorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String detailJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 管理端列表展示用，非表列；由仓储按 tenantId 批量填充 {@code sys_tenant.code}。 */
    @TableField(exist = false)
    private String tenantCode;

    @TableField(exist = false)
    private String tenantName;

    /** 当 actorType 为 USER 且 actorId 可解析为数字用户主键时，由仓储填充展示名。 */
    @TableField(exist = false)
    private String actorDisplayName;

    /** 管理端列表「关联对象」可读摘要（非表列）；从 {@code detail_json} 或租户上下文推导，避免主列仅展示成员行主键等裸 id。 */
    @TableField(exist = false)
    private String resourceDisplaySummary;

    /**
     * 管理端列表：{@code actorType} 为 {@code USER}（忽略大小写）或为空时，若 {@code actorId} 为数字则视为
     * {@code sec_user_account.id}；显式非 USER 类型（如服务账号）不解析，避免误关联。
     */
    public Long parseActorUserIdIfUser() {
        if (actorType != null && !actorType.isBlank() && !actorType.equalsIgnoreCase("USER")) {
            return null;
        }
        if (actorId == null || actorId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(actorId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
