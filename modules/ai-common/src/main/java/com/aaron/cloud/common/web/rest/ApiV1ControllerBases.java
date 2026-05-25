package com.aaron.cloud.common.web.rest;

import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 各资源在<strong>一条</strong>类级 {@code @RequestMapping} 上声明完整路径（{@link AbstractApiV1Controller#PREFIX} + 段），
 * 避免依赖 Spring 对多层父类 {@code @RequestMapping} 的合并实现。
 *
 * <p>具体控制器只标注 {@link org.springframework.web.bind.annotation.RestController} 并继承此处对应嵌套类型。
 */
public final class ApiV1ControllerBases {

    private ApiV1ControllerBases() {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/auth")
    public static abstract class Auth extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/user-profiles")
    public static abstract class AdminUserProfiles extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/users")
    public static abstract class AdminUsers extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/tenant-members")
    public static abstract class AdminTenantMembers extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/tenants")
    public static abstract class AdminTenants extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/me")
    public static abstract class AdminMe extends AbstractApiV1Controller {}

    /** 租户级运行时系统配置（免重启；Redis 读穿缓存） */
    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/tenant-runtime-settings")
    public static abstract class AdminTenantRuntimeSettings extends AbstractApiV1Controller {}

    /** 租户壳：品牌与出站（独立页；与通用 tenant-runtime-settings 分离） */
    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/tenant-shell-config")
    public static abstract class AdminTenantShellConfig extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin")
    public static abstract class AdminRead extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/rag")
    public static abstract class Rag extends AbstractApiV1Controller {}

    /** 首段 {@code {tenantCode}} 为 {@code sys_tenant.code}，其后为知识库主键等（与 C 端地址栏租户段语义一致）。 */
    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/rag-kbs/{tenantCode}")
    public static abstract class RagKbAdmin extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/job-tasks")
    public static abstract class AdminJobTasks extends AbstractApiV1Controller {}

    /** 租户隔离通用定时任务（类型枚举扩展；执行结果见入库任务 job_task）。 */
    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/scheduled-tasks")
    public static abstract class AdminScheduledTasks extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/mcp-servers")
    public static abstract class McpServers extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/llm-models")
    public static abstract class LlmModels extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/chat")
    public static abstract class AdminChat extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/mcp/tools")
    public static abstract class McpTools extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/notifications")
    public static abstract class Notifications extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/file")
    public static abstract class FileRoot extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/menu-items")
    public static abstract class AdminMenuItems extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-rate-limits")
    public static abstract class AdminGatewayRateLimits extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-api-endpoints")
    public static abstract class AdminGatewayApiEndpoints extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/cors-allowed-origins")
    public static abstract class AdminCorsAllowedOrigins extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-access-parties")
    public static abstract class AdminGatewayAccessParties extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-api-modules")
    public static abstract class AdminGatewayApiModules extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-access-party-grants")
    public static abstract class AdminGatewayAccessPartyGrants extends AbstractApiV1Controller {}

    @RequestMapping(AbstractApiV1Controller.PREFIX + "/admin/gateway-access-party-audit")
    public static abstract class AdminGatewayAccessPartyAudit extends AbstractApiV1Controller {}
}
