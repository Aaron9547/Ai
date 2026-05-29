package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.WebSearchFixedSourceOutboundRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 进程级 {@code ai.websearch.fixed.*} + 租户 Shell {@code WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON}。 */
@Component
@RequiredArgsConstructor
public class WebSearchFixedSourceOutboundResolver {

  private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

  private volatile WebSearchFixedSourceOutboundConfig processConfig;

  public void refreshProcessLevel(
      String proxyHost, Integer proxyPort, String proxyType, Boolean useSystemProxy) {
    processConfig =
        WebSearchFixedSourceOutboundConfig.resolve(proxyHost, proxyPort, proxyType, useSystemProxy);
  }

  public WebSearchFixedSourceOutboundConfig processConfig() {
    WebSearchFixedSourceOutboundConfig cfg = processConfig;
    if (cfg == null) {
      refreshProcessLevel(null, null, null, null);
      return processConfig;
    }
    return cfg;
  }

  public WebSearchFixedSourceOutboundConfig resolve(long tenantId) {
    WebSearchFixedSourceOutboundRuntime tenant =
        tenantRuntimeSettingApplicationService.webSearchFixedSourceOutbound(tenantId);
    return WebSearchFixedSourceOutboundConfig.resolveForTenant(tenant, processConfig());
  }

  public String diagnostics(long tenantId) {
    return WebSearchFixedSourceOutboundConfig.diagnostics(resolve(tenantId));
  }

  public String processDiagnostics() {
    return WebSearchFixedSourceOutboundConfig.diagnostics(processConfig());
  }
}
