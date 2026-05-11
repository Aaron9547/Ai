package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.enums.RagChunkRetrievalEnabled;
import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.mapper.RagChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * RAG 分块读路径；词法检索仅访问 {@code rag_chunk} 及关联 {@code lnk_*}，租户与知识库隔离在条件中显式表达。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RagChunkRepository {

    private final RagChunkMapper mapper;
    private final LnkRagDocumentChunkRepository lnkRagDocumentChunkRepository;
    private final RagDocumentRepository ragDocumentRepository;

    /**
     * 在当前租户与知识库范围内，对 {@code content} 做子串匹配（适合小库/本地；大库可换 FULLTEXT 或旁路索引）。
     *
     * @param kbId 知识库 id，经 {@code lnk_rag_kb_document} 约束文档范围
     */
    public List<String> searchSnippetTextsByLexical(long tenantId, long kbId, String query, int topK) {
        int limit = (int) Math.min(Math.max(1, topK), 50L);
        List<String> patterns = lexicalLikePatternsFromUserQuery(query);
        if (patterns.isEmpty()) {
            log.debug(
                    "ragLexicalSnippets skipped emptyLikePattern tenantId={} kbId={} topK={} rawQueryChars={}",
                    tenantId,
                    kbId,
                    limit,
                    query == null ? 0 : query.trim().length());
            return List.of();
        }
        var w =
                Wrappers.<RagChunk>lambdaQuery()
                        .select(RagChunk::getContent)
                        .eq(RagChunk::getTenantId, tenantId)
                        .eq(RagChunk::getDeleted, 0)
                        .eq(RagChunk::getRetrievalEnabled, RagChunkRetrievalEnabled.ENABLED)
                        .apply(
                                "EXISTS (SELECT 1 FROM lnk_rag_document_chunk dc INNER JOIN lnk_rag_kb_document lk ON lk.document_id = dc.document_id INNER JOIN rag_document d ON d.id = dc.document_id WHERE dc.chunk_id = rag_chunk.id AND lk.kb_id = {0} AND d.deleted = 0 AND d.tenant_id = {1})",
                                kbId,
                                tenantId);
        applyLexicalContentLikeOr(w, patterns);
        w.orderByDesc(RagChunk::getId).last("LIMIT " + limit);
        List<String> out =
                mapper.selectList(w).stream()
                        .map(RagChunk::getContent)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        log.debug(
                "ragLexicalSnippets tenantId={} kbId={} likePatternCount={} rawQueryChars={} resultCount={}",
                tenantId,
                kbId,
                patterns.size(),
                query == null ? 0 : query.trim().length(),
                out.size());
        return out;
    }

    /**
     * 与 {@link #searchSnippetTextsByLexical} 同序、同过滤条件，返回可审计的文档/分片引用（用于助手 meta 与命中计数）。
     */
    public List<RagCitationHit> searchCitationHitsByLexical(long tenantId, long kbId, String query, int topK) {
        int limit = (int) Math.min(Math.max(1, topK), 50L);
        List<String> patterns = lexicalLikePatternsFromUserQuery(query);
        if (patterns.isEmpty()) {
            log.debug(
                    "ragLexicalCitationHits skipped emptyLikePattern tenantId={} kbId={} topK={} rawQueryChars={}",
                    tenantId,
                    kbId,
                    limit,
                    query == null ? 0 : query.trim().length());
            return List.of();
        }
        var w =
                Wrappers.<RagChunk>lambdaQuery()
                        .select(RagChunk::getId, RagChunk::getContent)
                        .eq(RagChunk::getTenantId, tenantId)
                        .eq(RagChunk::getDeleted, 0)
                        .eq(RagChunk::getRetrievalEnabled, RagChunkRetrievalEnabled.ENABLED)
                        .apply(
                                "EXISTS (SELECT 1 FROM lnk_rag_document_chunk dc INNER JOIN lnk_rag_kb_document lk ON lk.document_id = dc.document_id INNER JOIN rag_document d ON d.id = dc.document_id WHERE dc.chunk_id = rag_chunk.id AND lk.kb_id = {0} AND d.deleted = 0 AND d.tenant_id = {1})",
                                kbId,
                                tenantId);
        applyLexicalContentLikeOr(w, patterns);
        w.orderByDesc(RagChunk::getId).last("LIMIT " + limit);
        List<RagChunk> chunks = mapper.selectList(w);
        if (chunks.isEmpty()) {
            log.debug(
                    "ragLexicalCitationHits noChunkRows tenantId={} kbId={} likePatternCount={} rawQueryChars={}",
                    tenantId,
                    kbId,
                    patterns.size(),
                    query == null ? 0 : query.trim().length());
            return List.of();
        }
        List<Long> chunkIds = chunks.stream().map(RagChunk::getId).filter(Objects::nonNull).toList();
        List<LnkRagDocumentChunk> lnks = lnkRagDocumentChunkRepository.listByChunkIds(chunkIds);
        Map<Long, LnkRagDocumentChunk> lnkByChunk =
                lnks.stream()
                        .collect(
                                Collectors.toMap(
                                        LnkRagDocumentChunk::getChunkId,
                                        Function.identity(),
                                        (a, b) -> a));
        List<Long> docIds =
                lnkByChunk.values().stream()
                        .map(LnkRagDocumentChunk::getDocumentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, RagDocument> docById = new HashMap<>();
        for (RagDocument d : ragDocumentRepository.listByIdsAndTenant(tenantId, docIds)) {
            docById.put(d.getId(), d);
        }
        List<RagCitationHit> out = new ArrayList<>();
        for (RagChunk ch : chunks) {
            if (ch.getId() == null) {
                continue;
            }
            LnkRagDocumentChunk lnk = lnkByChunk.get(ch.getId());
            if (lnk == null || lnk.getDocumentId() == null) {
                continue;
            }
            RagDocument doc = docById.get(lnk.getDocumentId());
            String title = doc != null && doc.getTitle() != null ? doc.getTitle() : "";
            int seq = lnk.getSeq() == null ? 0 : lnk.getSeq();
            String preview = previewText(ch.getContent());
            out.add(new RagCitationHit(kbId, lnk.getDocumentId(), title, ch.getId(), seq, preview));
        }
        log.debug(
                "ragLexicalCitationHits built tenantId={} kbId={} chunkRows={} citationCount={}",
                tenantId,
                kbId,
                chunks.size(),
                out.size());
        return out;
    }

    /**
     * 向量召回后按主键解析引用：校验分片属于指定知识库且可检索，再组装 {@link RagCitationHit}（不经 LIKE）。
     */
    public Optional<RagCitationHit> findCitationHitForKb(long tenantId, long kbId, long chunkId) {
        RagChunk ch =
                mapper.selectOne(
                        Wrappers.<RagChunk>lambdaQuery()
                                .eq(RagChunk::getId, chunkId)
                                .eq(RagChunk::getTenantId, tenantId)
                                .eq(RagChunk::getDeleted, 0)
                                .eq(RagChunk::getRetrievalEnabled, RagChunkRetrievalEnabled.ENABLED)
                                .apply(
                                        "EXISTS (SELECT 1 FROM lnk_rag_document_chunk dc INNER JOIN lnk_rag_kb_document lk ON lk.document_id = dc.document_id INNER JOIN rag_document d ON d.id = dc.document_id WHERE dc.chunk_id = rag_chunk.id AND lk.kb_id = {0} AND d.deleted = 0 AND d.tenant_id = {1})",
                                        kbId,
                                        tenantId));
        if (ch == null || ch.getId() == null) {
            return Optional.empty();
        }
        List<LnkRagDocumentChunk> lnks = lnkRagDocumentChunkRepository.listByChunkIds(List.of(ch.getId()));
        LnkRagDocumentChunk lnk =
                lnks.stream()
                        .filter(l -> ch.getId().equals(l.getChunkId()))
                        .findFirst()
                        .orElse(null);
        if (lnk == null) {
            return Optional.empty();
        }
        if (lnk.getDocumentId() == null) {
            return Optional.empty();
        }
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(lnk.getDocumentId(), tenantId);
        String title = doc != null && doc.getTitle() != null ? doc.getTitle() : "";
        int seq = lnk.getSeq() == null ? 0 : lnk.getSeq();
        String preview = previewText(ch.getContent());
        return Optional.of(new RagCitationHit(kbId, lnk.getDocumentId(), title, ch.getId(), seq, preview));
    }

    /**
     * 在多个知识库上顺序词法检索，按 {@code topK} 截断；同一段文本跨库只保留一条（去重顺序与遍历顺序一致）。
     */
    public List<String> searchSnippetTextsByLexicalForKbs(long tenantId, List<Long> kbIds, String query, int topK) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> acc = new LinkedHashSet<>();
        for (Long kbId : kbIds) {
            if (kbId == null) {
                continue;
            }
            for (String s : searchSnippetTextsByLexical(tenantId, kbId, query, topK)) {
                if (s == null) {
                    continue;
                }
                String t = s.trim();
                if (t.isEmpty()) {
                    continue;
                }
                acc.add(t);
                if (acc.size() >= topK) {
                    return new ArrayList<>(acc);
                }
            }
        }
        return new ArrayList<>(acc);
    }

    /**
     * 多库词法引用；同一 {@code chunkId} 只保留首次出现（通常唯一，跨库重复时按遍历顺序）。
     */
    public List<RagCitationHit> searchCitationHitsByLexicalForKbs(long tenantId, List<Long> kbIds, String query, int topK) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        Map<Long, RagCitationHit> byChunkId = new LinkedHashMap<>();
        for (Long kbId : kbIds) {
            if (kbId == null) {
                continue;
            }
            for (RagCitationHit h : searchCitationHitsByLexical(tenantId, kbId, query, topK)) {
                byChunkId.putIfAbsent(h.chunkId(), h);
                if (byChunkId.size() >= topK) {
                    return new ArrayList<>(byChunkId.values());
                }
            }
        }
        return new ArrayList<>(byChunkId.values());
    }

    /**
     * 从用户侧检索串构造 1～2 条 LIKE 子串：首部窗口 +（长文本时）尾部窗口，避免原先仅取前 120 字导致句末/附件侧关键词永远扫不到库。
     */
    private static List<String> lexicalLikePatternsFromUserQuery(String raw) {
        if (raw == null) {
            return List.of();
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return List.of();
        }
        final int headWindow = 4000;
        final int tailWindow = 500;
        final int tailThreshold = 600;
        String headRaw = t.length() <= headWindow ? t : t.substring(0, headWindow);
        String head = sanitizeForLikeSubstring(headRaw);
        List<String> out = new ArrayList<>();
        if (!head.isEmpty()) {
            out.add(head);
        }
        if (t.length() > tailThreshold) {
            String tailRaw = t.substring(Math.max(0, t.length() - tailWindow));
            String tail = sanitizeForLikeSubstring(tailRaw);
            if (!tail.isEmpty() && !tail.equals(head)) {
                out.add(tail);
            }
        }
        return out;
    }

    private static void applyLexicalContentLikeOr(LambdaQueryWrapper<RagChunk> w, List<String> patterns) {
        if (patterns.size() == 1) {
            w.like(RagChunk::getContent, patterns.getFirst());
            return;
        }
        w.and(
                nested -> {
                    nested.like(RagChunk::getContent, patterns.getFirst());
                    for (int i = 1; i < patterns.size(); i++) {
                        nested.or().like(RagChunk::getContent, patterns.get(i));
                    }
                });
    }

    private static String previewText(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('\n', ' ').trim();
        if (t.length() > 240) {
            return t.substring(0, 240) + "…";
        }
        return t;
    }

    /**
     * 去掉 LIKE 通配/转义字符，供 {@link #lexicalLikePatternsFromUserQuery} 传入的窗口子串使用（窗口长度在调用方控制）。
     */
    private static String sanitizeForLikeSubstring(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' || c == '_' || c == '\\') {
                continue;
            }
            b.append(c);
        }
        return b.toString().trim();
    }

    public RagChunk findByIdAndTenant(long id, long tenantId) {
        return mapper.selectOne(
                Wrappers.<RagChunk>lambdaQuery()
                        .eq(RagChunk::getId, id)
                        .eq(RagChunk::getTenantId, tenantId));
    }

    public int insert(RagChunk row) {
        return mapper.insert(row);
    }

    public int updateById(RagChunk row) {
        return mapper.updateById(row);
    }

    public List<RagChunk> listActiveByIdsOrdered(long tenantId, List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<RagChunk>lambdaQuery()
                        .eq(RagChunk::getTenantId, tenantId)
                        .eq(RagChunk::getDeleted, 0)
                        .in(RagChunk::getId, chunkIds));
    }

    public int markDeleted(long tenantId, List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long id : chunkIds) {
            RagChunk probe = findByIdAndTenant(id, tenantId);
            if (probe == null) {
                continue;
            }
            probe.setDeleted(1);
            n += mapper.updateById(probe);
        }
        return n;
    }

    /** 助手消息落库引用后累加分片命中（须带租户条件）。 */
    public int incrementHitCount(long tenantId, long chunkId) {
        return mapper.update(
                null,
                Wrappers.<RagChunk>lambdaUpdate()
                        .setSql("hit_count = IFNULL(hit_count, 0) + 1")
                        .eq(RagChunk::getId, chunkId)
                        .eq(RagChunk::getTenantId, tenantId)
                        .eq(RagChunk::getDeleted, 0));
    }
}
