package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.List;
import java.util.Optional;

/** 租户一次联网检索的源组合：可选火山 Ark 模型 + 若干内置固定源。 */
public record WebSearchGroundingPlan(Optional<SysLlmModel> arkModel, List<WebSearchFixedSource> fixedSources) {

    public boolean hasAnySource() {
        return arkModel.isPresent() || (fixedSources != null && !fixedSources.isEmpty());
    }
}
