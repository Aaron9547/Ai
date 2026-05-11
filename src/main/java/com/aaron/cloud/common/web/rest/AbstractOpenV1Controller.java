package com.aaron.cloud.common.web.rest;

/**
 * 标记「免 JWT 的 open v1」控制器族；对外路径前缀常量见 {@link #PREFIX}。
 *
 * <p>同 {@link AbstractApiV1Controller}：不在本类声明 {@code @RequestMapping}，由 {@link OpenV1ControllerBases} 使用
 * {@code PREFIX + 资源段} 声明单段完整类级路径。
 */
public abstract class AbstractOpenV1Controller {

    public static final String PREFIX = "/open/v1";
}
