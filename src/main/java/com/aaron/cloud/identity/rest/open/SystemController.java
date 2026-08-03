package com.aaron.cloud.identity.rest.open;

import com.aaron.cloud.common.context.LoginContextUtils;
import com.aaron.cloud.common.context.LoginUser;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import com.aaron.cloud.identity.tenant.TenantBrandingResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SystemController extends OpenV1ControllerBases.OpenSystem {

    private final SysTenantRepository sysTenantRepository;
    private final TenantBrandingResolver tenantBrandingResolver;

    /**
     * 当前租户上下文快照；访客 {@code userId} 可能为 {@code null}，不可用 {@link Map#of}（禁止 null 值）。
     *
     * <p>若存在 {@code tenantId}，附带 {@code tenantName}、{@code tenantCode}（库中无记录时为 {@code null}），供 C 端展示「名称（代码）」。
     *
     * <p>已登录且存在 {@code userId} 时附带 {@code loginName}、{@code displayName}（来自线程内 {@link LoginUser} 快照），不在此接口返回数字型用户主键以外的敏感字段。
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        var snap = TenantContextHolder.getOrNull();
        Map<String, Object> out = new LinkedHashMap<>(8);
        if (snap == null) {
            out.put("tenantId", null);
            out.put("tenantName", null);
            out.put("tenantCode", null);
            out.put("userId", null);
            out.put("loginName", null);
            out.put("displayName", null);
            out.put("deviceId", null);
            return out;
        }
        out.put("tenantId", snap.getTenantId());
        out.put("userId", snap.getUserId());
        out.put("deviceId", snap.getDeviceId());
        LoginUser user = LoginContextUtils.getUser();
        if (user != null) {
            out.put("loginName", user.getLoginName());
            out.put("displayName", user.getDisplayName());
        } else {
            out.put("loginName", null);
            out.put("displayName", null);
        }
        Long tid = snap.getTenantId();
        if (tid != null) {
            sysTenantRepository
                    .findById(tid)
                    .ifPresentOrElse(
                            t -> {
                                out.put("tenantName", t.getName());
                                out.put("tenantCode", t.getCode());
                            },
                            () -> {
                                out.put("tenantName", null);
                                out.put("tenantCode", null);
                            });
        } else {
            out.put("tenantName", null);
            out.put("tenantCode", null);
        }
        return out;
    }

    /**
     * 租户外观（与管理端 Shell「管理端外观」同源：{@code sys_tenant.admin_logo_url} 等），供 C 端侧栏/登录/分享图展示。
     */
    @GetMapping("/tenant-branding")
    public Map<String, Object> tenantBranding() {
        var snap = TenantContextHolder.getOrNull();
        Map<String, Object> out = new LinkedHashMap<>(6);
        if (snap == null || snap.getTenantId() == null) {
            out.put("logoUrl", "");
            out.put("portalTitle", "");
            out.put("footerText", "");
            out.put("portalTitleResolved", "");
            return out;
        }
        var branding = tenantBrandingResolver.resolve(snap.getTenantId());
        out.put("logoUrl", branding.logoUrl());
        out.put("portalTitle", branding.portalTitle());
        out.put("footerText", branding.footerText());
        out.put("portalTitleResolved", branding.portalTitleResolved());
        return out;
    }
}
