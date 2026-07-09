package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService.PutItem;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService.PlatformSettingRow;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 平台级系统参数（全租户共享）；更新后约 30 秒内全进程快照刷新，无需重启。 */
@RestController
@RequiredArgsConstructor
public class AdminPlatformSettingsRestController extends ApiV1ControllerBases.AdminPlatformSettings {

    private final PlatformSettingApplicationService platformSettingApplicationService;

    @GetMapping
    public List<PlatformSettingRow> list() {
        assertFounderForPlatformSettings();
        return platformSettingApplicationService.listEffectiveRows();
    }

    @PutMapping
    public void replace(@RequestBody ReplaceBody body) {
        assertFounderForPlatformSettings();
        platformSettingApplicationService.replace(body.getItems());
    }

    /** 平台参数为全租户共享配置；仅创始人可读写，避免租户管理员误改全局 Cron/TTL 等。 */
    private static void assertFounderForPlatformSettings() {
        var snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (role == null || !role.isFounder()) {
            throw new AccessDeniedException("仅创始人可管理平台系统参数");
        }
    }

    @Data
    public static class ReplaceBody {
        private List<PutItem> items;
    }
}
