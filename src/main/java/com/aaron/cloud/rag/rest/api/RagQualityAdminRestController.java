package com.aaron.cloud.rag.rest.api;

import com.aaron.cloud.common.api.enums.rag.RagQualityAssessmentScope;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.rag.dto.RagQualityAdminDtos.RagQualityAssessmentSubmitRequest;
import com.aaron.cloud.rag.dto.RagQualityAdminDtos.RagQualityAssessmentView;
import com.aaron.cloud.rag.quality.RagQualityAssessmentService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RagQualityAdminRestController extends ApiV1ControllerBases.AdminRagQuality {

    private final RagQualityAssessmentService ragQualityAssessmentService;

    @PostMapping("/assessments")
    public RagQualityAssessmentView submit(@RequestBody RagQualityAssessmentSubmitRequest req, Locale locale) {
        long adminId = TenantContextHolder.require().getUserId() != null ? TenantContextHolder.require().getUserId() : 0L;
        return ragQualityAssessmentService.submit(req, adminId, locale);
    }

    @GetMapping("/assessments/{runId}")
    public RagQualityAssessmentView get(@PathVariable String runId) {
        return ragQualityAssessmentService.getByRunId(runId);
    }

    @GetMapping("/assessments")
    public Object list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId,
            @RequestParam(required = false) RagQualityAssessmentScope scope,
            @RequestParam(required = false) String conversationKeyword,
            @RequestParam(required = false) String queryKeyword,
            @RequestParam(defaultValue = "30") int days) {
        return ragQualityAssessmentService.pageForAdmin(
                filterTenantId, page, size, scope, conversationKeyword, queryKeyword, days);
    }
}
