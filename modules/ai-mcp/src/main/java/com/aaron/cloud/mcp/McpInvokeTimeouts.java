package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class McpInvokeTimeouts {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    public Duration toolTimeout(long tenantId) {
        String raw =
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.MCP_CHAT_TOOL_TIMEOUT_SECONDS);
        int sec;
        try {
            sec = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sec = 60;
        }
        if (sec < 5) {
            sec = 5;
        }
        if (sec > 300) {
            sec = 300;
        }
        return Duration.ofSeconds(sec);
    }
}
