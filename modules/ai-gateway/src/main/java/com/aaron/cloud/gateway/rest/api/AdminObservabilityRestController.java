package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.admin.AdminObservabilityApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminObservabilityRestController extends ApiV1ControllerBases.AdminObservability {

    private final AdminObservabilityApplicationService adminObservabilityApplicationService;

    @GetMapping("/mcp-traces")
    public Object mcpTraces(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String conversationKeyword,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "7") int days) {
        return adminObservabilityApplicationService.pageMcpTraces(
                filterTenantId, page, size, conversationId, conversationKeyword, toolName, success, days);
    }

    @GetMapping("/mcp-traces/{traceId}")
    public Object mcpTraceDetail(@PathVariable String traceId) {
        return adminObservabilityApplicationService.getMcpTrace(traceId);
    }

    @GetMapping("/rag-hits")
    public Object ragHits(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String conversationKeyword,
            @RequestParam(required = false) String queryKeyword,
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String kbNameKeyword,
            @RequestParam(required = false) Long chunkId,
            @RequestParam(defaultValue = "7") int days) {
        return adminObservabilityApplicationService.pageRagHits(
                filterTenantId,
                page,
                size,
                conversationId,
                conversationKeyword,
                queryKeyword,
                kbId,
                kbNameKeyword,
                chunkId,
                days);
    }

    @GetMapping("/rag-hits/{hitTraceId}")
    public Object ragHitBatch(@PathVariable String hitTraceId) {
        return adminObservabilityApplicationService.listRagHitsByTraceId(hitTraceId);
    }

    @GetMapping("/conversations/{conversationId}/traces")
    public Object conversationTraces(@PathVariable long conversationId) {
        return adminObservabilityApplicationService.conversationTraces(conversationId);
    }
}
