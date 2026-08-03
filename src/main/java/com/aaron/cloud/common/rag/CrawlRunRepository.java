package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.CrawlRun;
import com.aaron.cloud.common.rag.mapper.CrawlRunMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CrawlRunRepository {

    private final CrawlRunMapper mapper;

    public void insert(CrawlRun row) {
        mapper.insert(row);
    }

    public void updateById(CrawlRun row) {
        mapper.updateById(row);
    }

    public Optional<CrawlRun> findById(long tenantId, long runId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<CrawlRun>lambdaQuery()
                                .eq(CrawlRun::getTenantId, tenantId)
                                .eq(CrawlRun::getId, runId)));
    }

    public int deleteOlderThan(LocalDateTime cutoff) {
        return mapper.delete(Wrappers.<CrawlRun>lambdaQuery().lt(CrawlRun::getCreatedAt, cutoff));
    }

    public int deleteBySiteId(long tenantId, long kbId, long siteId) {
        return mapper.delete(
                Wrappers.<CrawlRun>lambdaQuery()
                        .eq(CrawlRun::getTenantId, tenantId)
                        .eq(CrawlRun::getKbId, kbId)
                        .eq(CrawlRun::getSiteId, siteId));
    }

    public List<CrawlRun> listByKb(long tenantId, long kbId, int limit, Long siteId) {
        int lim = Math.min(Math.max(limit, 1), 100);
        var q =
                Wrappers.<CrawlRun>lambdaQuery()
                        .eq(CrawlRun::getTenantId, tenantId)
                        .eq(CrawlRun::getKbId, kbId)
                        .orderByDesc(CrawlRun::getCreatedAt)
                        .last("LIMIT " + lim);
        if (siteId != null) {
            q.eq(CrawlRun::getSiteId, siteId);
        }
        return mapper.selectList(q);
    }
}
