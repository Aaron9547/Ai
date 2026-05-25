package com.aaron.cloud.gateway;

import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiCatalogService;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiSpecPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayApiEndpointOpenApiSyncApplicationService {

    private final GwApiEndpointRepository endpointRepository;
    private final GwApiOpenApiCatalogService openApiCatalogService;

    @Transactional
    public SyncResult syncAll(boolean emptyOnly) {
        List<GwApiEndpoint> rows = endpointRepository.listAll();
        int updated = 0;
        int skipped = 0;
        int unmatched = 0;
        List<String> unmatchedSamples = new ArrayList<>();
        for (GwApiEndpoint row : rows) {
            SyncOneResult one = syncRow(row, emptyOnly);
            switch (one.status()) {
                case UPDATED -> {
                    endpointRepository.updateById(one.row());
                    updated++;
                }
                case SKIPPED -> skipped++;
                case UNMATCHED -> {
                    unmatched++;
                    if (unmatchedSamples.size() < 20) {
                        unmatchedSamples.add(row.getHttpMethod() + " " + row.getPathPattern());
                    }
                }
            }
        }
        return new SyncResult(updated, skipped, unmatched, unmatchedSamples);
    }

    @Transactional
    public GwApiEndpoint syncOne(long id, boolean emptyOnly) {
        GwApiEndpoint row = endpointRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("endpoint not found");
        }
        SyncOneResult result = syncRow(row, emptyOnly);
        if (result.status() == SyncStatus.UNMATCHED) {
            throw new IllegalArgumentException("no matching OpenAPI operation");
        }
        if (result.status() == SyncStatus.SKIPPED) {
            return row;
        }
        endpointRepository.updateById(row);
        return endpointRepository.findById(id);
    }

    private SyncOneResult syncRow(GwApiEndpoint row, boolean emptyOnly) {
        Optional<GwApiOpenApiSpecPair> pair =
                openApiCatalogService.resolve(row.getPathPattern(), row.getHttpMethod());
        if (pair.isEmpty()) {
            return new SyncOneResult(SyncStatus.UNMATCHED, row);
        }
        GwApiOpenApiSpecPair spec = pair.get();
        boolean requestBlank = GwApiEndpointSpecSupport.isBlankSpec(row.getRequestSpecJson());
        boolean responseBlank = GwApiEndpointSpecSupport.isBlankSpec(row.getResponseSpecJson());
        boolean requestFromOpenApi = !GwApiEndpointSpecSupport.isBlankSpec(spec.requestSpecJson());
        boolean responseFromOpenApi = !GwApiEndpointSpecSupport.isBlankSpec(spec.responseSpecJson());
        if (!requestFromOpenApi && !responseFromOpenApi) {
            return new SyncOneResult(SyncStatus.UNMATCHED, row);
        }
        boolean changed = false;
        if (requestFromOpenApi && (!emptyOnly || requestBlank)) {
            row.setRequestSpecJson(spec.requestSpecJson());
            changed = true;
        }
        if (responseFromOpenApi && (!emptyOnly || responseBlank)) {
            row.setResponseSpecJson(spec.responseSpecJson());
            changed = true;
        }
        if (!changed) {
            return new SyncOneResult(SyncStatus.SKIPPED, row);
        }
        return new SyncOneResult(SyncStatus.UPDATED, row);
    }

    public enum SyncStatus {
        UPDATED,
        SKIPPED,
        UNMATCHED
    }

    private record SyncOneResult(SyncStatus status, GwApiEndpoint row) {}

    public record SyncResult(int updated, int skipped, int unmatched, List<String> unmatchedSamples) {}
}
