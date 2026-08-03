package com.aaron.cloud.common.api.identity;

import com.aaron.cloud.common.api.enums.identity.AccountPrincipalKind;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 管理端 REST 路径段 {@code /admin/users/{account}}、{@code /tenant-members/{account}} 编解码。 */
public final class AccountPrincipalPaths {

    private AccountPrincipalPaths() {}

    /**
     * 解析路径变量：{@code ac:U12AB34CD56EF}、{@code ln:zhangsan}、{@code em:user@x.com}、{@code ph:13800138000}；
     * 无 {@code :} 时整段视为 {@link AccountPrincipalKind#LOGIN_NAME}（兼容旧客户端）。
     */
    public static AccountPrincipalRef parsePathSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("account path required");
        }
        String decoded = URLDecoder.decode(raw.trim(), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        if (colon > 0 && colon < decoded.length() - 1) {
            String prefix = decoded.substring(0, colon).trim();
            String val = decoded.substring(colon + 1).trim();
            if (val.isEmpty()) {
                throw new IllegalArgumentException("account path value empty");
            }
            return new AccountPrincipalRef(AccountPrincipalKind.fromPathPrefix(prefix), val);
        }
        return AccountPrincipalRef.loginName(decoded);
    }

    /** 生成路径段（调用方须再经 URL 编码嵌入 path）。 */
    public static String toPathSegment(AccountPrincipalRef ref) {
        if (ref.kind() == AccountPrincipalKind.LOGIN_NAME) {
            return AccountPrincipalKind.LOGIN_NAME.getPathPrefix() + ":" + ref.value();
        }
        return ref.kind().getPathPrefix() + ":" + ref.value();
    }
}
