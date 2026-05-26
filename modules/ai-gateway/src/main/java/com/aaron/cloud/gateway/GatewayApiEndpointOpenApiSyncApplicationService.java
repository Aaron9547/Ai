package com.aaron.cloud.gateway;

import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiCatalogService;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiLookupResult;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiSpecPair;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayApiEndpointOpenApiSyncApplicationService {

    private static final int DEFAULT_BATCH_LIMIT = 50;
    private static final int MAX_BATCH_LIMIT = 200;

    private final GwApiEndpointRepository endpointRepository;
    private final GwApiOpenApiCatalogService openApiCatalogService;

    @Transactional
    public SyncResult syncAll(boolean emptyOnly) {
        List<GwApiEndpoint> rows = endpointRepository.listAll();
        OpenAPI openApi = openApiCatalogService.openApiCatalog().orElse(null);
        int updated = 0;
        int skippedAlreadyFilled = 0;
        int skippedNoSchema = 0;
        int unmatched = 0;
        List<String> unmatchedSamples = new ArrayList<>();
        for (GwApiEndpoint row : rows) {
            SyncOneResult one = syncRow(row, emptyOnly, openApi);
            switch (one.status()) {
                case UPDATED -> {
                    endpointRepository.updateById(one.row());
                    updated++;
                }
                case SKIPPED_ALREADY_FILLED -> skippedAlreadyFilled++;
                case SKIPPED_NO_SCHEMA -> skippedNoSchema++;
                case UNMATCHED -> {
                    unmatched++;
                    if (unmatchedSamples.size() < 20) {
                        unmatchedSamples.add(row.getHttpMethod() + " " + row.getPathPattern());
                    }
                }
            }
        }
        return new SyncResult(
                updated,
                skippedAlreadyFilled,
                skippedNoSchema,
                unmatched,
                unmatchedSamples,
                openApi != null,
                openApiCatalogService.openApiPathCount(openApi));
    }

    @Transactional
    public BatchSyncResult syncBatch(int offset, int limit, boolean emptyOnly) {
        int safeLimit = limit <= 0 ? DEFAULT_BATCH_LIMIT : Math.min(limit, MAX_BATCH_LIMIT);
        int safeOffset = Math.max(0, offset);
        List<GwApiEndpoint> all = endpointRepository.listAll();
        int total = all.size();
        OpenAPI openApi = openApiCatalogService.openApiCatalog().orElse(null);
        int openApiPathCount = openApiCatalogService.openApiPathCount(openApi);
        if (safeOffset >= total) {
            return emptyBatchResult(total, openApi != null, openApiPathCount);
        }
        int end = Math.min(safeOffset + safeLimit, total);
        int batchUpdated = 0;
        int batchSkippedAlreadyFilled = 0;
        int batchNoSchema = 0;
        int batchUnmatched = 0;
        List<String> unmatchedSamples = new ArrayList<>();
        for (int i = safeOffset; i < end; i++) {
            GwApiEndpoint row = all.get(i);
            SyncOneResult one = syncRow(row, emptyOnly, openApi);
            switch (one.status()) {
                case UPDATED -> {
                    endpointRepository.updateById(one.row());
                    batchUpdated++;
                }
                case SKIPPED_ALREADY_FILLED -> batchSkippedAlreadyFilled++;
                case SKIPPED_NO_SCHEMA -> batchNoSchema++;
                case UNMATCHED -> {
                    batchUnmatched++;
                    if (unmatchedSamples.size() < 10) {
                        unmatchedSamples.add(row.getHttpMethod() + " " + row.getPathPattern());
                    }
                }
            }
        }
        int processed = end;
        boolean done = processed >= total;
        return new BatchSyncResult(
                total,
                processed,
                batchUpdated,
                batchSkippedAlreadyFilled,
                batchNoSchema,
                batchUnmatched,
                safeOffset,
                done,
                unmatchedSamples,
                openApi != null,
                openApiPathCount);
    }

    private static BatchSyncResult emptyBatchResult(int total, boolean openApiReady, int openApiPathCount) {
        return new BatchSyncResult(
                total, total, 0, 0, 0, 0, 0, true, List.of(), openApiReady, openApiPathCount);
    }

    @Transactional
    public GwApiEndpoint syncOne(long id, boolean emptyOnly) {
        GwApiEndpoint row = endpointRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("endpoint not found");
        }
        OpenAPI openApi = openApiCatalogService.openApiCatalog().orElse(null);
        SyncOneResult result = syncRow(row, emptyOnly, openApi);
        if (result.status() == SyncStatus.UNMATCHED) {
            throw new IllegalArgumentException("no matching OpenAPI operation");
        }
        if (result.status() == SyncStatus.SKIPPED_NO_SCHEMA) {
            throw new IllegalArgumentException("OpenAPI operation has no request/response schema to sync");
        }
        if (result.status() == SyncStatus.SKIPPED_ALREADY_FILLED) {
            return row;
        }
        endpointRepository.updateById(row);
        return endpointRepository.findById(id);
    }

    private SyncOneResult syncRow(GwApiEndpoint row, boolean emptyOnly, OpenAPI openApi) {
        GwApiOpenApiLookupResult lookup = openApi == null
                ? openApiCatalogService.lookup(
                        openApiCatalogService.openApiCatalog().orElse(null),
                        row.getPathPattern(),
                        row.getHttpMethod())
                : openApiCatalogService.lookup(openApi, row.getPathPattern(), row.getHttpMethod());
        if (!lookup.operationFound()) {
            return new SyncOneResult(SyncStatus.UNMATCHED, row);
        }
        if (!lookup.hasExtractableSpec()) {
            return new SyncOneResult(SyncStatus.SKIPPED_NO_SCHEMA, row);
        }
        GwApiOpenApiSpecPair spec = lookup.spec();
        boolean requestBlank = GwApiEndpointSpecSupport.isBlankSpec(row.getRequestSpecJson());
        boolean responseBlank = GwApiEndpointSpecSupport.isBlankSpec(row.getResponseSpecJson());
        boolean requestFromOpenApi = !GwApiEndpointSpecSupport.isBlankSpec(spec.requestSpecJson());
        boolean responseFromOpenApi = !GwApiEndpointSpecSupport.isBlankSpec(spec.responseSpecJson());
        boolean changed = false;
        if (requestFromOpenApi && shouldApply(requestBlank, emptyOnly)) {
            if (!Objects.equals(normalizeSpecJson(row.getRequestSpecJson()), normalizeSpecJson(spec.requestSpecJson()))) {
                row.setRequestSpecJson(spec.requestSpecJson());
                changed = true;
            }
        }
        if (responseFromOpenApi && shouldApply(responseBlank, emptyOnly)) {
            if (!Objects.equals(normalizeSpecJson(row.getResponseSpecJson()), normalizeSpecJson(spec.responseSpecJson()))) {
                row.setResponseSpecJson(spec.responseSpecJson());
                changed = true;
            }
        }
        if (!changed) {
            return new SyncOneResult(SyncStatus.SKIPPED_ALREADY_FILLED, row);
        }
        return new SyncOneResult(SyncStatus.UPDATED, row);
    }

    private static boolean shouldApply(boolean fieldBlank, boolean emptyOnly) {
        return !emptyOnly || fieldBlank;
    }

    private static String normalizeSpecJson(String json) {
        return json == null ? "" : json.trim();
    }

    public enum SyncStatus {
        UPDATED,
        SKIPPED_ALREADY_FILLED,
        /** OpenAPI 有对应操作，但未解析出入参/出参字段（如 SSE、204、无 @Schema）。 */
        SKIPPED_NO_SCHEMA,
        /** 路径/方法在 OpenAPI 中无对应操作。 */
        UNMATCHED
    }

    private record SyncOneResult(SyncStatus status, GwApiEndpoint row) {}

    public record SyncResult(
            int updated,
            int skippedAlreadyFilled,
            int skippedNoSchema,
            int unmatched,
            List<String> unmatchedSamples,
            boolean openApiReady,
            int openApiPathCount) {

        public int skipped() {
            return skippedAlreadyFilled;
        }
    }

    public record BatchSyncResult(
            int total,
            int processed,
            int batchUpdated,
            int batchSkippedAlreadyFilled,
            int batchNoSchema,
            int batchUnmatched,
            int offset,
            boolean done,
            List<String> unmatchedSamples,
            boolean openApiReady,
            int openApiPathCount) {

        public int batchSkipped() {
            return batchSkippedAlreadyFilled;
        }
    }
}
