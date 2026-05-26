package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.SummaryResponse;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.UniverseGraphResponse;
import com.aaron.cloud.chat.dto.ChatKnowledgePlanetDtos.WeeklyLatestResponse;
import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetQueryService;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatKnowledgePlanetController extends OpenV1ControllerBases.Chat {

    private final KnowledgePlanetQueryService queryService;

    @GetMapping("/knowledge-planet/summary")
    public SummaryResponse summary() {
        return queryService.summary();
    }

    /** 星系视图：主题星球 + 知识点节点 + 轨道/关联连线（供 3d-force-graph）。 */
    @GetMapping("/knowledge-planet/universe")
    public UniverseGraphResponse universe() {
        return queryService.universeGraph();
    }

    @GetMapping("/knowledge-planet/weekly/latest")
    public WeeklyLatestResponse weeklyLatest() {
        return queryService.weeklyLatest();
    }
}
