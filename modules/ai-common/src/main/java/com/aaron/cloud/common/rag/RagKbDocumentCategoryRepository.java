package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.RagKbDocumentCategory;
import com.aaron.cloud.common.rag.mapper.RagKbDocumentCategoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagKbDocumentCategoryRepository {

    private final RagKbDocumentCategoryMapper mapper;

    public List<RagKbDocumentCategory> listByKb(long tenantId, long kbId) {
        return mapper.selectList(
                Wrappers.<RagKbDocumentCategory>lambdaQuery()
                        .eq(RagKbDocumentCategory::getTenantId, tenantId)
                        .eq(RagKbDocumentCategory::getKbId, kbId)
                        .orderByAsc(RagKbDocumentCategory::getSortOrder)
                        .orderByAsc(RagKbDocumentCategory::getId));
    }

    public Optional<RagKbDocumentCategory> findById(long tenantId, long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<RagKbDocumentCategory>lambdaQuery()
                                .eq(RagKbDocumentCategory::getTenantId, tenantId)
                                .eq(RagKbDocumentCategory::getId, id)));
    }

    public int insert(RagKbDocumentCategory row) {
        return mapper.insert(row);
    }

    public int updateById(RagKbDocumentCategory row) {
        return mapper.updateById(row);
    }

    public int delete(long tenantId, long id) {
        return mapper.delete(
                Wrappers.<RagKbDocumentCategory>lambdaQuery()
                        .eq(RagKbDocumentCategory::getTenantId, tenantId)
                        .eq(RagKbDocumentCategory::getId, id));
    }

    public int deleteByKb(long tenantId, long kbId) {
        return mapper.delete(
                Wrappers.<RagKbDocumentCategory>lambdaQuery()
                        .eq(RagKbDocumentCategory::getTenantId, tenantId)
                        .eq(RagKbDocumentCategory::getKbId, kbId));
    }
}
