package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.api.enums.ScheduledTaskIntervalPreset;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.rag.RagKbDocumentCategoryRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagWebCrawlSiteAdminView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.RagWebCrawlSiteUpsertRequest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RagWebCrawlSiteAdminApplicationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RagWebCrawlSiteRepository siteRepository;
    private final RagKnowledgeBaseRepository kbRepository;
    private final RagKbDocumentCategoryRepository categoryRepository;
    private final RagApplicationService ragApplicationService;
    private final RagWebCrawlExtractConfigSupport extractConfigSupport;

    public Map<String, Object> siteMeta() {
        return Map.of(
                "syncModes", RagWebCrawlSyncMode.metaList(),
                "schedulePresets", ScheduledTaskIntervalPreset.metaList(),
                "contentExtractors",
                List.of(
                        Map.of("code", "jsoup", "label", "Jsoup 规则"),
                        Map.of("code", "readability", "label", "Readability")));
    }

    public List<RagWebCrawlSiteAdminView> list(long kbId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        return siteRepository.listByKb(tenantId, kbId).stream().map(this::toView).toList();
    }

    public RagWebCrawlSiteAdminView create(long kbId, RagWebCrawlSiteUpsertRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        var row = new RagWebCrawlSite();
        row.setTenantId(tenantId);
        row.setKbId(kbId);
        applyUpsert(row, req, tenantId, kbId);
        siteRepository.insert(row);
        return toView(siteRepository.findById(tenantId, kbId, row.getId()).orElseThrow());
    }

    public RagWebCrawlSiteAdminView update(long kbId, long siteId, RagWebCrawlSiteUpsertRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        RagWebCrawlSite row =
                siteRepository
                        .findById(tenantId, kbId, siteId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "site not found"));
        applyUpsert(row, req, tenantId, kbId);
        siteRepository.updateById(row);
        return toView(siteRepository.findById(tenantId, kbId, siteId).orElseThrow());
    }

    public void delete(long kbId, long siteId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        if (siteRepository.findById(tenantId, kbId, siteId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "site not found");
        }
        siteRepository.delete(tenantId, kbId, siteId);
    }

    public void runNow(long kbId, long siteId) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        RagWebCrawlSite site =
                siteRepository
                        .findById(tenantId, kbId, siteId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "site not found"));
        var mode = RagWebCrawlSiteSupport.resolveEffectiveSyncMode(site);
        ragApplicationService.enqueueSiteCrawlJobForSite(
                tenantId,
                site.getKbId(),
                site.getBaseUrl(),
                mode,
                site.getMaxDepth(),
                site.getFilterCrawled() != null && site.getFilterCrawled() == 1,
                site.getChunkStrategy(),
                site.getCategoryId(),
                site.getId());
        site.setLastCrawlAt(BeijingTime.nowLocal());
        siteRepository.updateById(site);
    }

    private void applyUpsert(RagWebCrawlSite row, RagWebCrawlSiteUpsertRequest req, long tenantId, long kbId) {
        row.setName(req.getName().trim());
        row.setBaseUrl(req.getBaseUrl().trim());
        boolean scheduling = req.getEnabled() != null && req.getEnabled();
        row.setEnabled(scheduling ? 1 : 0);
        if (scheduling) {
            row.setSchedulePreset(ScheduledTaskIntervalPreset.fromCode(req.getSchedulePreset()));
            row.setRunAtTime(parseRunAt(req.getRunAtTime()));
        } else {
            row.setSchedulePreset(ScheduledTaskIntervalPreset.MANUAL);
            row.setRunAtTime(null);
        }
        validateCategory(tenantId, kbId, req.getCategoryId());
        row.setCategoryId(req.getCategoryId());
        row.setChunkStrategy(
                req.getChunkStrategy() != null
                        ? req.getChunkStrategy()
                        : com.aaron.cloud.common.api.enums.RagChunkStrategy.SEMANTIC.getCode());
        row.setExtractConfig(
                req.getExtractConfig() != null ? extractConfigSupport.toJson(req.getExtractConfig()) : null);
        row.setSyncMode(
                RagWebCrawlSyncMode.fromCode(
                        req.getSyncMode() != null ? req.getSyncMode() : RagWebCrawlSyncMode.FULL.getCode()));
        row.setMaxDepth(req.getMaxDepth() != null ? req.getMaxDepth() : 3);
        row.setFilterCrawled(req.getFilterCrawled() == null || req.getFilterCrawled() ? 1 : 0);
    }

    private void validateCategory(long tenantId, long kbId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        RagKbDocumentCategory cat =
                categoryRepository
                        .findById(tenantId, categoryId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category"));
        if (!Objects.equals(cat.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category");
        }
    }

    private void requireKb(long kbId, long tenantId) {
        if (kbRepository.findByIdAndTenant(kbId, tenantId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
    }

    private static LocalTime parseRunAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalTime.parse(raw.trim(), TIME_FMT);
    }

    private RagWebCrawlSiteAdminView toView(RagWebCrawlSite s) {
        return new RagWebCrawlSiteAdminView(
                s.getId(),
                s.getKbId(),
                s.getName(),
                s.getBaseUrl(),
                s.getSchedulePreset() != null ? s.getSchedulePreset().getCode() : null,
                s.getSchedulePreset() != null ? s.getSchedulePreset().getLabel() : null,
                s.getRunAtTime() != null ? s.getRunAtTime().format(TIME_FMT) : null,
                s.getEnabled() != null && s.getEnabled() == 1,
                s.getFirstRunDone() != null && s.getFirstRunDone() == 1,
                s.getLastCrawlAt() != null ? s.getLastCrawlAt().format(ISO) : null,
                s.getCategoryId(),
                s.getChunkStrategy(),
                s.getSyncMode() != null ? s.getSyncMode().getCode() : null,
                s.getSyncMode() != null ? s.getSyncMode().getLabel() : null,
                s.getMaxDepth(),
                s.getFilterCrawled() != null && s.getFilterCrawled() == 1,
                extractConfigSupport.fromJson(s.getExtractConfig()),
                s.getCreatedAt() != null ? s.getCreatedAt().format(ISO) : null,
                s.getUpdatedAt() != null ? s.getUpdatedAt().format(ISO) : null);
    }
}
