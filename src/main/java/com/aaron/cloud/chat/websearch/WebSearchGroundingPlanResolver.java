package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        List<SysLlmModel> arkModels = resolveArkModels(tenantId);
        List<WebSearchFixedSource> fixed =
                tenantRuntimeSettingApplicationService.webSearchGroundingFixedSources(tenantId);
        return new WebSearchGroundingPlan(arkModels, fixed);
    }

    /** 今日智能洞察：仅用「联网检索源」中的联网模型，忽略内置固定源。 */
    public WebSearchGroundingPlan resolveModelsOnly(long tenantId) {
        return resolve(tenantId).withoutFixedSources();
    }

    public boolean isAvailable(long tenantId) {
        return resolve(tenantId).hasAnySource();
    }

    public boolean isModelsAvailable(long tenantId) {
        return resolveModelsOnly(tenantId).hasAnyModel();
    }

    private List<SysLlmModel> resolveArkModels(long tenantId) {
        List<Long> configuredIds = tenantRuntimeSettingApplicationService.webSearchGroundingModelIds(tenantId);
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<SysLlmModel> out = new ArrayList<>();
        for (Long id : configuredIds) {
            if (id == null || id <= 0L || !seen.add(id)) {
                continue;
            }
            resolveWebSearchModel(tenantId, Optional.of(id)).ifPresent(out::add);
        }
        if (out.isEmpty()) {
            resolveWebSearchModel(tenantId, Optional.empty()).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private Optional<SysLlmModel> resolveWebSearchModel(long tenantId, Optional<Long> explicitId) {
        return llmModelRepository.resolveWebSearchModel(
                tenantId,
                explicitId.or(
                        () -> tenantRuntimeSettingApplicationService.webSearchGroundingModelId(tenantId)));
    }
}
