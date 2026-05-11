package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.PutItem;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.TenantRuntimeSettingRow;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户级系统运行参数；列表与更新均绑定 {@link com.aaron.cloud.common.context.TenantContextHolder} 当前租户，请求体<strong>不得</strong>携带跨租户标识。
 * 更新后读库路径立即生效；Redis 读穿缓存由服务层双删失效。
 */
@RestController
@RequiredArgsConstructor
public class AdminTenantRuntimeSettingsRestController extends ApiV1ControllerBases.AdminTenantRuntimeSettings {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @GetMapping
    public List<TenantRuntimeSettingRow> list() {
        return tenantRuntimeSettingApplicationService.listEffectiveRows(TenantContextHolder.require().getTenantId());
    }

    @PutMapping
    public void replace(@RequestBody ReplaceBody body) {
        tenantRuntimeSettingApplicationService.replace(
                TenantContextHolder.require().getTenantId(), body.getItems());
    }

    @Data
    public static class ReplaceBody {
        private List<PutItem> items;
    }
}
