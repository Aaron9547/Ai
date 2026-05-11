package com.aaron.cloud.rag.rest.api;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.rag.RagApplicationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RagRestController extends ApiV1ControllerBases.Rag {

    private final RagApplicationService ragApplicationService;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;

    @PostMapping("/kbs")
    public RagKnowledgeBase createKb(@RequestBody CreateKbBody body) {
        return ragApplicationService.createKb(body.getName());
    }

    @GetMapping("/kbs")
    public List<RagKnowledgeBase> listKbs() {
        var snap = TenantContextHolder.require();
        return ragKnowledgeBaseRepository.listByTenant(snap.getTenantId());
    }

    @PostMapping("/kbs/{kbId}/index-jobs")
    public IndexJobResponse enqueue(@PathVariable long kbId) throws Exception {
        long jobId = ragApplicationService.enqueueIndexJob(kbId);
        return new IndexJobResponse(jobId);
    }

    @Data
    public static class CreateKbBody {
        @NotBlank private String name;
    }

    public record IndexJobResponse(long jobTaskId) {}
}
