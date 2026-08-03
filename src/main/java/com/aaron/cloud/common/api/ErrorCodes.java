package com.aaron.cloud.common.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {

    public static final String TENANT_REQUIRED = "TENANT_REQUIRED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION = "VALIDATION";
    public static final String FORBIDDEN = "FORBIDDEN";
    /** 开放登录：账号状态非 ACTIVE */
    public static final String LOGIN_ACCOUNT_DISABLED = "LOGIN_ACCOUNT_DISABLED";
    /** 开放登录：无 ACTIVE 的租户成员关系（或成员 status 无法映射为 ACTIVE） */
    public static final String LOGIN_NO_ACTIVE_MEMBERSHIP = "LOGIN_NO_ACTIVE_MEMBERSHIP";
    /** 切换管理端工作与角色：与成员关系不符或无权使用该角色。 */
    public static final String ADMIN_CONTEXT_DENIED = "ADMIN_CONTEXT_DENIED";
    /** API 响应 {@code code}：邀请时目标用户在该租户下已有 ACTIVE 成员行（HTTP 409，由 GlobalExceptionHandler 映射）。 */
    public static final String TENANT_MEMBER_ALREADY_ACTIVE = "TENANT_MEMBER_ALREADY_ACTIVE";
    /** API 响应 {@code code}：改角色/移除等要求成员行为 ACTIVE（HTTP 400，由 GlobalExceptionHandler 映射）。 */
    public static final String TENANT_MEMBER_INACTIVE = "TENANT_MEMBER_INACTIVE";
    /**
     * 仅供 {@code IllegalStateException#getMessage()} 使用，且须与 {@code GlobalExceptionHandler} 分支字符串完全一致；见 {@code
     * PROJECT.md}「成员状态与业务错误」。
     */
    public static final String EX_MSG_TENANT_MEMBER_ALREADY_ACTIVE = "tenant member already active";

    /**
     * 仅供 {@code IllegalArgumentException#getMessage()} 使用，且须与 {@code GlobalExceptionHandler} 分支字符串完全一致。
     */
    public static final String EX_MSG_TENANT_MEMBER_INACTIVE = "inactive tenant membership";

    /** 管理端：禁止对本人执行踢下线、封禁、启停等账号级操作（{@code IllegalArgumentException#getMessage()} 契约）。 */
    public static final String EX_MSG_CANNOT_OPERATE_ON_SELF = "cannot_operate_on_self";

    /** 管理端：目标用户不在当前 JWT 租户成员关系中（{@code AccessDeniedException} 映射用中文 message）。 */
    public static final String USER_NOT_IN_CURRENT_TENANT = "USER_NOT_IN_CURRENT_TENANT";

    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL = "INTERNAL";

    /**
     * 仅供 {@link IllegalStateException#getMessage()} 使用，且须与 {@code GlobalExceptionHandler} 分支字符串完全一致（管理端创建/改登录名冲突）。
     */
    public static final String EX_MSG_LOGIN_NAME_CONFLICT = "login_name_conflict";

    public static final String LOGIN_NAME_CONFLICT = "LOGIN_NAME_CONFLICT";

    /** 开放注册：邮箱格式无效 */
    public static final String REGISTER_EMAIL_INVALID = "REGISTER_EMAIL_INVALID";
    /** 开放注册：验证码错误或已失效 */
    public static final String REGISTER_CODE_INVALID = "REGISTER_CODE_INVALID";
    /** 开放注册：密码强度不足 */
    public static final String REGISTER_PASSWORD_WEAK = "REGISTER_PASSWORD_WEAK";

    /** 模型共用 token 等配额已用尽 */
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

    /** 接入方 HMAC 鉴权失败 */
    public static final String ACCESS_PARTY_UNAUTHORIZED = "ACCESS_PARTY_UNAUTHORIZED";
    /** 接入方未授权访问该接口 */
    public static final String ACCESS_PARTY_ENDPOINT_DENIED = "ACCESS_PARTY_ENDPOINT_DENIED";
    /** 接入方 RPM 超限 */
    public static final String ACCESS_PARTY_RATE_LIMITED = "ACCESS_PARTY_RATE_LIMITED";
}
