package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.RagWebCrawlUrlItem;
import com.aaron.cloud.common.rag.mapper.RagWebCrawlUrlItemMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagWebCrawlUrlItemRepository {

    private final RagWebCrawlUrlItemMapper mapper;

    public List<RagWebCrawlUrlItem> listActiveBySchedule(long tenantId, long scheduleId) {
        return mapper.selectList(
                Wrappers.<RagWebCrawlUrlItem>lambdaQuery()
                        .eq(RagWebCrawlUrlItem::getTenantId, tenantId)
                        .eq(RagWebCrawlUrlItem::getScheduleId, scheduleId)
                        .eq(RagWebCrawlUrlItem::getDeleted, 0));
    }

    public List<RagWebCrawlUrlItem> listAllBySchedule(long tenantId, long scheduleId) {
        return mapper.selectList(
                Wrappers.<RagWebCrawlUrlItem>lambdaQuery()
                        .eq(RagWebCrawlUrlItem::getTenantId, tenantId)
                        .eq(RagWebCrawlUrlItem::getScheduleId, scheduleId));
    }

    public Set<String> activeUrlsForTenant(long tenantId) {
        return mapper
                .selectList(
                        Wrappers.<RagWebCrawlUrlItem>lambdaQuery()
                                .eq(RagWebCrawlUrlItem::getTenantId, tenantId)
                                .eq(RagWebCrawlUrlItem::getDeleted, 0)
                                .select(RagWebCrawlUrlItem::getUrl))
                .stream()
                .map(RagWebCrawlUrlItem::getUrl)
                .collect(Collectors.toSet());
    }

    public Set<String> activeUrlsForSchedule(long tenantId, long scheduleId) {
        return listActiveBySchedule(tenantId, scheduleId).stream()
                .map(RagWebCrawlUrlItem::getUrl)
                .collect(Collectors.toSet());
    }

    public int insert(RagWebCrawlUrlItem row) {
        return mapper.insert(row);
    }

    public int markDeletedBySchedule(long tenantId, long scheduleId) {
        var patch = new RagWebCrawlUrlItem();
        patch.setDeleted(1);
        return mapper.update(
                patch,
                Wrappers.<RagWebCrawlUrlItem>lambdaQuery()
                        .eq(RagWebCrawlUrlItem::getTenantId, tenantId)
                        .eq(RagWebCrawlUrlItem::getScheduleId, scheduleId)
                        .eq(RagWebCrawlUrlItem::getDeleted, 0));
    }

    public int markDeletedByDocumentIds(long tenantId, Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        var patch = new RagWebCrawlUrlItem();
        patch.setDeleted(1);
        return mapper.update(
                patch,
                Wrappers.<RagWebCrawlUrlItem>lambdaQuery()
                        .eq(RagWebCrawlUrlItem::getTenantId, tenantId)
                        .in(RagWebCrawlUrlItem::getDocumentId, documentIds)
                        .eq(RagWebCrawlUrlItem::getDeleted, 0));
    }
}
