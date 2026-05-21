package com.aaron.cloud.rag.crawl;

import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.entity.CrawlUrlQueue;
import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.discovery.UrlMergeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将合并后的 URL 写入 {@code crawl_url_queue}（同 run 同 url_norm 合并 sources）。 */
@Service
@RequiredArgsConstructor
public class CrawlUrlQueueWriter {

    private final CrawlUrlQueueRepository crawlUrlQueueRepository;
    private final ObjectMapper objectMapper;

    public void upsertMerged(long runId, long tenantId, List<UrlMergeService.MergedUrl> merged) {
        for (UrlMergeService.MergedUrl m : merged) {
            if (m.url() == null || m.url().isBlank()) {
                continue;
            }
            String norm = RagWebCrawlUrlSupport.normalizeUrl(m.url());
            if (norm.isEmpty()) {
                continue;
            }
            Optional<CrawlUrlQueue> existing = crawlUrlQueueRepository.findByRunAndNorm(runId, norm);
            if (existing.isEmpty()) {
                CrawlUrlQueue q = new CrawlUrlQueue();
                q.setRunId(runId);
                q.setTenantId(tenantId);
                q.setUrl(m.url());
                q.setUrlNorm(norm);
                q.setQueueRole(m.role().name());
                q.setStatus(CrawlQueueStatus.PENDING.name());
                q.setScore(m.score());
                q.setRetryCount(0);
                q.setSourcesJson(writeSources(m.sources()));
                crawlUrlQueueRepository.insert(q);
            } else {
                CrawlUrlQueue row = existing.get();
                if (m.score() > row.getScore()) {
                    row.setScore(m.score());
                }
                if (m.role() == CrawlQueueRole.ARTICLE) {
                    row.setQueueRole(CrawlQueueRole.ARTICLE.name());
                }
                row.setSourcesJson(mergeSourcesJson(row.getSourcesJson(), m.sources()));
                crawlUrlQueueRepository.updateById(row);
            }
        }
    }

    private String writeSources(List<String> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String mergeSourcesJson(String existingJson, List<String> add) {
        try {
            var arr = objectMapper.createArrayNode();
            if (existingJson != null && !existingJson.isBlank()) {
                var old = objectMapper.readTree(existingJson);
                if (old.isArray()) {
                    for (var el : old) {
                        arr.add(el.asText());
                    }
                }
            }
            for (String s : add) {
                boolean dup = false;
                for (var el : arr) {
                    if (el.asText().equals(s)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    arr.add(s);
                }
            }
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            return writeSources(add);
        }
    }
}
