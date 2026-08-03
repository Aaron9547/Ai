package com.aaron.cloud.common.security.entity;

import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
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

    /** 应用侧雪花主键（非数据库自增），仅库内/FK 使用，不对外 API 暴露。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对外账号编号（如 {@code U12AB34CD56EF}），全局唯一；管理端路径推荐 {@code ac:} 主体。 */
    private String accountNo;

    /** 密码登录凭证，全局唯一；可与邮箱/手机相同或独立用户名。 */
    private String loginName;

    /** 绑定邮箱（小写规范化）；唯一，可空。 */
    private String email;

    /** 绑定手机号（规范化）；唯一，可空。 */
    private String phone;

    private UserRegistrationChannel registrationChannel;

    /** 注册完成时间（UTC）；与 {@link #createdAt} 可相同。 */
    private LocalDateTime registeredAt;

    /** 昵称或对外展示名；空串时前端可回退登录名/账号编号。 */
    private String displayName;

    private String passwordHash;
    private UserAccountStatus status;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long jwtSeq;

    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String lastLoginRegion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
