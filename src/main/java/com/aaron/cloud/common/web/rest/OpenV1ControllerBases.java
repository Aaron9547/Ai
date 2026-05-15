package com.aaron.cloud.common.web.rest;

import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 各开放资源在<strong>一条</strong>类级 {@code @RequestMapping} 上声明完整路径（{@link AbstractOpenV1Controller#PREFIX} + 段）。
 *
 * <p>具体控制器只标注 {@link org.springframework.web.bind.annotation.RestController} 并继承此处对应嵌套类型。
 */
public final class OpenV1ControllerBases {

    private OpenV1ControllerBases() {}

    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/auth")
    public static abstract class Auth extends AbstractOpenV1Controller {}

    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/system")
    public static abstract class OpenSystem extends AbstractOpenV1Controller {}

    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/profile")
    public static abstract class Profile extends AbstractOpenV1Controller {}

    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/chat")
    public static abstract class Chat extends AbstractOpenV1Controller {}

    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/chat/conversations")
    public static abstract class ChatConversations extends AbstractOpenV1Controller {}

    /** 管理端租户 LOGO 匿名读取（{@code <img src>} 无 Authorization）。 */
    @RequestMapping(AbstractOpenV1Controller.PREFIX + "/admin-brand-logos")
    public static abstract class AdminBrandLogos extends AbstractOpenV1Controller {}
}
