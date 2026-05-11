package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.LnkRagKbDocument;
import com.aaron.cloud.common.rag.mapper.LnkRagKbDocumentMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LnkRagKbDocumentRepository {

    private final LnkRagKbDocumentMapper mapper;

    public long countByKbId(long kbId) {
        return mapper.selectCount(Wrappers.<LnkRagKbDocument>lambdaQuery().eq(LnkRagKbDocument::getKbId, kbId));
    }

    public int insert(LnkRagKbDocument row) {
        return mapper.insert(row);
    }

    public boolean existsKbDocument(long kbId, long documentId) {
        return mapper.selectCount(
                        Wrappers.<LnkRagKbDocument>lambdaQuery()
                                .eq(LnkRagKbDocument::getKbId, kbId)
                                .eq(LnkRagKbDocument::getDocumentId, documentId))
                > 0;
    }

    public int deleteLink(long kbId, long documentId) {
        return mapper.delete(
                Wrappers.<LnkRagKbDocument>lambdaQuery()
                        .eq(LnkRagKbDocument::getKbId, kbId)
                        .eq(LnkRagKbDocument::getDocumentId, documentId));
    }
}
