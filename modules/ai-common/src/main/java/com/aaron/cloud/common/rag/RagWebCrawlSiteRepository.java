package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;
import com.aaron.cloud.common.rag.mapper.RagWebCrawlSiteMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagWebCrawlSiteRepository {

    private final RagWebCrawlSiteMapper mapper;

    public List<RagWebCrawlSite> listByKb(long tenantId, long kbId) {
        return mapper.selectList(
                Wrappers.<RagWebCrawlSite>lambdaQuery()
                        .eq(RagWebCrawlSite::getTenantId, tenantId)
                        .eq(RagWebCrawlSite::getKbId, kbId)
                        .orderByDesc(RagWebCrawlSite::getUpdatedAt));
    }

    public List<RagWebCrawlSite> listAllEnabledForTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<RagWebCrawlSite>lambdaQuery()
                        .eq(RagWebCrawlSite::getTenantId, tenantId)
                        .eq(RagWebCrawlSite::getEnabled, 1));
    }

    public Optional<RagWebCrawlSite> findByIdForTenant(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<RagWebCrawlSite>lambdaQuery()
                                .eq(RagWebCrawlSite::getTenantId, tenantId)
                                .eq(RagWebCrawlSite::getId, id)));
    }

    public Optional<RagWebCrawlSite> findById(long tenantId, long kbId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<RagWebCrawlSite>lambdaQuery()
                                .eq(RagWebCrawlSite::getTenantId, tenantId)
                                .eq(RagWebCrawlSite::getKbId, kbId)
                                .eq(RagWebCrawlSite::getId, id)));
    }

    public int insert(RagWebCrawlSite row) {
        return mapper.insert(row);
    }

    public int updateById(RagWebCrawlSite row) {
        return mapper.updateById(row);
    }

    public int delete(long tenantId, long kbId, long id) {
        return mapper.delete(
                Wrappers.<RagWebCrawlSite>lambdaQuery()
                        .eq(RagWebCrawlSite::getTenantId, tenantId)
                        .eq(RagWebCrawlSite::getKbId, kbId)
                        .eq(RagWebCrawlSite::getId, id));
    }
}
