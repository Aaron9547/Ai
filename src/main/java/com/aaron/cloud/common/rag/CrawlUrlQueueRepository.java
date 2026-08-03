package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.CrawlUrlQueue;
import com.aaron.cloud.common.rag.mapper.CrawlUrlQueueMapper;
import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;
import com.aaron.cloud.common.api.enums.rag.CrawlQueueStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CrawlUrlQueueRepository {

    private final CrawlUrlQueueMapper mapper;

    public void insert(CrawlUrlQueue row) {
        mapper.insert(row);
    }

    public void updateById(CrawlUrlQueue row) {
        mapper.updateById(row);
    }

    public java.util.Optional<CrawlUrlQueue> findByRunAndNorm(long runId, String urlNorm) {
        return java.util.Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<CrawlUrlQueue>lambdaQuery()
                                .eq(CrawlUrlQueue::getRunId, runId)
                                .eq(CrawlUrlQueue::getUrlNorm, urlNorm)
                                .last("LIMIT 1")));
    }

    public List<CrawlUrlQueue> listPendingExplore(long runId, int limit) {
        return mapper.selectList(
                Wrappers.<CrawlUrlQueue>lambdaQuery()
                        .eq(CrawlUrlQueue::getRunId, runId)
                        .eq(CrawlUrlQueue::getQueueRole, CrawlQueueRole.EXPLORE.name())
                        .eq(CrawlUrlQueue::getStatus, CrawlQueueStatus.PENDING.name())
                        .orderByDesc(CrawlUrlQueue::getScore)
                        .last("LIMIT " + Math.max(1, limit)));
    }

    /** 按 score 降序保留前 maxKeep 条 PENDING ARTICLE，其余标记为 SKIPPED_FILTERED。 */
    public int capPendingArticles(long runId, int maxKeep) {
        if (maxKeep <= 0) {
            return 0;
        }
        List<CrawlUrlQueue> pending =
                mapper.selectList(
                        Wrappers.<CrawlUrlQueue>lambdaQuery()
                                .eq(CrawlUrlQueue::getRunId, runId)
                                .eq(CrawlUrlQueue::getQueueRole, CrawlQueueRole.ARTICLE.name())
                                .eq(CrawlUrlQueue::getStatus, CrawlQueueStatus.PENDING.name())
                                .orderByDesc(CrawlUrlQueue::getScore));
        if (pending.size() <= maxKeep) {
            return 0;
        }
        int trimmed = 0;
        for (int i = maxKeep; i < pending.size(); i++) {
            CrawlUrlQueue row = pending.get(i);
            row.setStatus(CrawlQueueStatus.SKIPPED_FILTERED.name());
            row.setErrorCode("MAX_ARTICLES_CAP");
            mapper.updateById(row);
            trimmed++;
        }
        return trimmed;
    }

    public int countPendingArticles(long runId) {
        Long n =
                mapper.selectCount(
                        Wrappers.<CrawlUrlQueue>lambdaQuery()
                                .eq(CrawlUrlQueue::getRunId, runId)
                                .eq(CrawlUrlQueue::getQueueRole, CrawlQueueRole.ARTICLE.name())
                                .eq(CrawlUrlQueue::getStatus, CrawlQueueStatus.PENDING.name()));
        return n == null ? 0 : n.intValue();
    }

    public void markArticlesSkipped(long runId, java.util.Set<String> urlNorms, String errorCode) {
        if (urlNorms == null || urlNorms.isEmpty()) {
            return;
        }
        CrawlUrlQueue patch = new CrawlUrlQueue();
        patch.setStatus(CrawlQueueStatus.SKIPPED_FILTERED.name());
        patch.setErrorCode(errorCode);
        mapper.update(
                patch,
                Wrappers.<CrawlUrlQueue>lambdaQuery()
                        .eq(CrawlUrlQueue::getRunId, runId)
                        .eq(CrawlUrlQueue::getQueueRole, CrawlQueueRole.ARTICLE.name())
                        .eq(CrawlUrlQueue::getStatus, CrawlQueueStatus.PENDING.name())
                        .in(CrawlUrlQueue::getUrlNorm, urlNorms));
    }

    public List<CrawlUrlQueue> listPendingArticles(long runId, int limit) {
        return mapper.selectList(
                Wrappers.<CrawlUrlQueue>lambdaQuery()
                        .eq(CrawlUrlQueue::getRunId, runId)
                        .eq(CrawlUrlQueue::getQueueRole, "ARTICLE")
                        .eq(CrawlUrlQueue::getStatus, "PENDING")
                        .orderByDesc(CrawlUrlQueue::getScore)
                        .last("LIMIT " + Math.max(1, limit)));
    }

    public List<CrawlUrlQueue> listFailed(long runId) {
        return mapper.selectList(
                Wrappers.<CrawlUrlQueue>lambdaQuery()
                        .eq(CrawlUrlQueue::getRunId, runId)
                        .eq(CrawlUrlQueue::getStatus, "FAILED"));
    }

    /** 将 FAILED 行重置为 PENDING，供「仅重试失败项」运维入队新 run 前或同 run 恢复使用。 */
    public void bumpRetryCount(long queueId) {
        CrawlUrlQueue row = mapper.selectById(queueId);
        if (row == null) {
            return;
        }
        int rc = row.getRetryCount() == null ? 0 : row.getRetryCount();
        row.setRetryCount(rc + 1);
        mapper.updateById(row);
    }

    public int resetFailedToPending(long runId) {
        CrawlUrlQueue patch = new CrawlUrlQueue();
        patch.setStatus(CrawlQueueStatus.PENDING.name());
        patch.setErrorCode(null);
        patch.setRetryCount(0);
        return mapper.update(
                patch,
                Wrappers.<CrawlUrlQueue>lambdaQuery()
                        .eq(CrawlUrlQueue::getRunId, runId)
                        .eq(CrawlUrlQueue::getStatus, CrawlQueueStatus.FAILED.name()));
    }

    public Map<String, Long> countByStatus(long runId) {
        return mapper
                .selectList(
                        Wrappers.<CrawlUrlQueue>lambdaQuery()
                                .eq(CrawlUrlQueue::getRunId, runId)
                                .select(CrawlUrlQueue::getStatus))
                .stream()
                .collect(Collectors.groupingBy(CrawlUrlQueue::getStatus, Collectors.counting()));
    }

    public int deleteByRunIds(List<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return 0;
        }
        return mapper.delete(Wrappers.<CrawlUrlQueue>lambdaQuery().in(CrawlUrlQueue::getRunId, runIds));
    }

    public List<Long> findRunIdsOlderThan(LocalDateTime cutoff) {
        return mapper
                .selectList(
                        Wrappers.<CrawlUrlQueue>lambdaQuery()
                                .lt(CrawlUrlQueue::getCreatedAt, cutoff)
                                .select(CrawlUrlQueue::getRunId)
                                .groupBy(CrawlUrlQueue::getRunId))
                .stream()
                .map(CrawlUrlQueue::getRunId)
                .distinct()
                .toList();
    }
}
