package com.aaron.cloud.common.web.rest;

/**
 * 标记「须 JWT 的 HTTP API v1」控制器族；对外路径前缀常量见 {@link #PREFIX}。
 *
 * <p>Spring MVC 对<strong>类型链上多个类级</strong>{@code @RequestMapping} 的合并行为易随版本变化且不可靠，
 * 故不在本类声明路径，而在 {@link ApiV1ControllerBases} 的各嵌套类型上使用 {@code PREFIX + 资源段} 的<strong>单段完整类级路径</strong>。
 *
 * <p>具体 {@code @RestController} 勿再写类级 {@code @RequestMapping}。
 */
public abstract class AbstractApiV1Controller {

    public static final String PREFIX = "/api/v1";
}
