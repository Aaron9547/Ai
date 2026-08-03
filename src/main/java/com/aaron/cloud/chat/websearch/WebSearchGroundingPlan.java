package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.List;
import java.util.Optional;

/** 租户一次联网检索的源组合：若干火山 Ark 联网模型 + 可选内置固定源。 */
public record WebSearchGroundingPlan(List<SysLlmModel> arkModels, List<WebSearchFixedSource> fixedSources) {

    public WebSearchGroundingPlan {
        arkModels = arkModels == null ? List.of() : List.copyOf(arkModels);
        fixedSources = fixedSources == null ? List.of() : List.copyOf(fixedSources);
    }

    /** 兼容单模型读路径（取列表首项）。 */
    public Optional<SysLlmModel> arkModel() {
        return arkModels.isEmpty() ? Optional.empty() : Optional.of(arkModels.getFirst());
    }

    public boolean hasAnySource() {
        return !arkModels.isEmpty() || !fixedSources.isEmpty();
    }

    public boolean hasAnyModel() {
        return !arkModels.isEmpty();
    }

    /** 今日洞察等场景：仅保留联网模型，去掉内置固定源。 */
    public WebSearchGroundingPlan withoutFixedSources() {
        return new WebSearchGroundingPlan(arkModels, List.of());
    }
}
