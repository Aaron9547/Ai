package com.aaron.cloud.common.security.entity;

import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sec_user_account")
public class SecUserAccount {

    /** 应用侧雪花主键（非数据库自增），便于分布式与合并；外键列仍为 {@code user_id BIGINT}。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录凭证，全局唯一；与 {@link #displayName}（昵称/展示名）语义分离。 */
    private String loginName;

    /** 昵称或对外展示名；可与 {@link #loginName} 不同；空串时前端可回退显示登录名。 */
    private String displayName;

    private String passwordHash;
    private UserAccountStatus status;

    /**
     * 递增后此前签发的 JWT 全部失效（踢下线）。未迁移列前不参与 INSERT/UPDATE，避免 INSERT 引用不存在的列。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long jwtSeq;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
