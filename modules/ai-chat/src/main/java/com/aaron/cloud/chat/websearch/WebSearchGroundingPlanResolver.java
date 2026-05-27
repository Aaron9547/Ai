package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSearchGroundingPlanResolver {

    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    public WebSearchGroundingPlan resolve(long tenantId) {
        Optional<SysLlmModel> ark =
                llmModelRepository.resolveWebSearchModel(
                        tenantId,
                        tenantRuntimeSettingApplicationService.webSearchGroundingModelId(tenantId));
        List<WebSearchFixedSource> fixed =
                tenantRuntimeSettingApplicationService.webSearchGroundingFixedSources(tenantId);
        return new WebSearchGroundingPlan(ark, fixed);
    }

    public boolean isAvailable(long tenantId) {
        return resolve(tenantId).hasAnySource();
    }
}
