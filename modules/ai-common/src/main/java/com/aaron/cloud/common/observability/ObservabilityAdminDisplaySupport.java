package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.observability.entity.ObsMcpTraceEvent;
import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.rag.entity.RagQualityAssessment;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 管理端链路可观测列表：批量补齐会话标题、用户提问、知识库/文档/分片可读名称。 */
@Component
@RequiredArgsConstructor
public class ObservabilityAdminDisplaySupport {

    private static final int TEXT_PREVIEW_LEN = 120;

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final RagChunkRepository ragChunkRepository;

    public void enrichMcpTraces(List<ObsMcpTraceEvent> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> titles = loadConversationTitles(rows.stream().map(ObsMcpTraceEvent::getConversationId).toList());
        for (ObsMcpTraceEvent row : rows) {
            if (row.getConversationId() != null) {
                row.setConversationTitle(titles.get(row.getConversationId()));
            }
        }
    }

    public void enrichRagHits(List<ObsRagHitEvent> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> titles = loadConversationTitles(rows.stream().map(ObsRagHitEvent::getConversationId).toList());
        Map<Long, String> questions = loadUserQuestions(rows);
        Map<Long, String> kbNames = loadKbNames(rows);
        Map<Long, String> docTitles = loadDocumentTitles(rows);
        Map<Long, RagChunk> chunks = loadChunks(rows);

        for (ObsRagHitEvent row : rows) {
            if (row.getConversationId() != null) {
                row.setConversationTitle(titles.get(row.getConversationId()));
            }
            if (row.getUserMessageId() != null) {
                row.setUserQuestionPreview(questions.get(row.getUserMessageId()));
            }
            if (row.getKbId() != null) {
                row.setKbName(kbNames.get(row.getKbId()));
            }
            if (row.getDocumentId() != null) {
                row.setDocumentTitle(docTitles.get(row.getDocumentId()));
            }
            row.setChunkLabel(buildChunkLabel(row, docTitles.get(row.getDocumentId()), chunks.get(row.getChunkId())));
        }
    }

    public void enrichQualityAssessments(List<RagQualityAssessment> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> titles = loadConversationTitles(rows.stream().map(RagQualityAssessment::getConversationId).toList());
        Map<Long, String> kbNames = loadKbNamesForQuality(rows);
        Map<Long, RagChunk> chunks = loadChunksForQuality(rows);

        for (RagQualityAssessment row : rows) {
            if (row.getConversationId() != null) {
                row.setConversationTitle(titles.get(row.getConversationId()));
            }
            if (row.getKbId() != null) {
                row.setKbName(kbNames.get(row.getKbId()));
            }
            if (row.getChunkId() != null) {
                RagChunk chunk = chunks.get(row.getChunkId());
                row.setChunkLabel(buildChunkLabel(null, null, chunk));
            }
        }
    }

    public String conversationTitle(long conversationId) {
        return chatConversationRepository
                .findByIdForAdmin(conversationId)
                .map(ChatConversation::getTitle)
                .filter(t -> !t.isBlank())
                .orElse(null);
    }

    public String userQuestionPreview(long tenantId, long userMessageId) {
        return chatMessageRepository
                .findById(userMessageId, tenantId)
                .map(ChatMessage::getContent)
                .map(ObservabilityAdminDisplaySupport::preview)
                .orElse(null);
    }

    public String kbName(long tenantId, long kbId) {
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        return kb != null && kb.getName() != null && !kb.getName().isBlank() ? kb.getName().trim() : null;
    }

    public String chunkLabel(long tenantId, long chunkId) {
        RagChunk chunk = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        return buildChunkLabel(null, null, chunk);
    }

    private Map<Long, String> loadConversationTitles(Collection<Long> conversationIds) {
        Set<Long> ids = nonNullIds(conversationIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        for (Long id : ids) {
            chatConversationRepository.findByIdForAdmin(id).ifPresent(c -> {
                if (c.getTitle() != null && !c.getTitle().isBlank()) {
                    out.put(id, c.getTitle().trim());
                }
            });
        }
        return out;
    }

    private Map<Long, String> loadUserQuestions(List<ObsRagHitEvent> rows) {
        Map<Long, String> out = new HashMap<>();
        for (ObsRagHitEvent row : rows) {
            if (row.getUserMessageId() == null || row.getTenantId() == null) {
                continue;
            }
            Long key = row.getUserMessageId();
            if (out.containsKey(key)) {
                continue;
            }
            chatMessageRepository
                    .findById(key, row.getTenantId())
                    .map(ChatMessage::getContent)
                    .map(ObservabilityAdminDisplaySupport::preview)
                    .ifPresent(text -> out.put(key, text));
        }
        return out;
    }

    private Map<Long, String> loadKbNames(List<ObsRagHitEvent> rows) {
        Map<Long, String> out = new HashMap<>();
        for (ObsRagHitEvent row : rows) {
            if (row.getKbId() == null || row.getTenantId() == null || out.containsKey(row.getKbId())) {
                continue;
            }
            RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(row.getKbId(), row.getTenantId());
            if (kb != null && kb.getName() != null && !kb.getName().isBlank()) {
                out.put(row.getKbId(), kb.getName().trim());
            }
        }
        return out;
    }

    private Map<Long, String> loadKbNamesForQuality(List<RagQualityAssessment> rows) {
        Map<Long, String> out = new HashMap<>();
        for (RagQualityAssessment row : rows) {
            if (row.getKbId() == null || row.getTenantId() == null || out.containsKey(row.getKbId())) {
                continue;
            }
            RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(row.getKbId(), row.getTenantId());
            if (kb != null && kb.getName() != null && !kb.getName().isBlank()) {
                out.put(row.getKbId(), kb.getName().trim());
            }
        }
        return out;
    }

    private Map<Long, RagChunk> loadChunksForQuality(List<RagQualityAssessment> rows) {
        Map<Long, RagChunk> out = new HashMap<>();
        for (RagQualityAssessment row : rows) {
            if (row.getChunkId() == null || row.getTenantId() == null || out.containsKey(row.getChunkId())) {
                continue;
            }
            RagChunk chunk = ragChunkRepository.findByIdAndTenant(row.getChunkId(), row.getTenantId());
            if (chunk != null) {
                out.put(row.getChunkId(), chunk);
            }
        }
        return out;
    }

    private Map<Long, String> loadDocumentTitles(List<ObsRagHitEvent> rows) {
        Map<Long, String> out = new HashMap<>();
        for (ObsRagHitEvent row : rows) {
            if (row.getDocumentId() == null || row.getTenantId() == null || out.containsKey(row.getDocumentId())) {
                continue;
            }
            RagDocument doc = ragDocumentRepository.findByIdAndTenant(row.getDocumentId(), row.getTenantId());
            if (doc != null) {
                String title = documentTitle(doc);
                if (title != null) {
                    out.put(row.getDocumentId(), title);
                }
            }
        }
        return out;
    }

    private Map<Long, RagChunk> loadChunks(List<ObsRagHitEvent> rows) {
        Map<Long, RagChunk> out = new HashMap<>();
        for (ObsRagHitEvent row : rows) {
            if (row.getChunkId() == null || row.getTenantId() == null || out.containsKey(row.getChunkId())) {
                continue;
            }
            RagChunk chunk = ragChunkRepository.findByIdAndTenant(row.getChunkId(), row.getTenantId());
            if (chunk != null) {
                out.put(row.getChunkId(), chunk);
            }
        }
        return out;
    }

    private static String buildChunkLabel(ObsRagHitEvent row, String documentTitle, RagChunk chunk) {
        String doc = documentTitle;
        if (doc == null && row != null && row.getDocumentId() != null) {
            doc = "#" + row.getDocumentId();
        }
        Integer seq = row != null ? row.getChunkSeq() : null;
        String contentPreview = chunk != null ? preview(chunk.getContent()) : null;
        if (doc != null && seq != null) {
            return doc + " #" + seq;
        }
        if (doc != null && contentPreview != null) {
            return doc + " · " + contentPreview;
        }
        if (contentPreview != null) {
            return contentPreview;
        }
        if (row != null && row.getChunkId() != null) {
            return "#" + row.getChunkId();
        }
        return null;
    }

    private static String documentTitle(RagDocument doc) {
        if (doc.getTitle() != null && !doc.getTitle().isBlank()) {
            return doc.getTitle().trim();
        }
        if (doc.getOriginalFilename() != null && !doc.getOriginalFilename().isBlank()) {
            return doc.getOriginalFilename().trim();
        }
        return null;
    }

    private static Set<Long> nonNullIds(Collection<Long> ids) {
        Set<Long> out = new HashSet<>();
        if (ids == null) {
            return out;
        }
        for (Long id : ids) {
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    static String preview(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("\\s{2,}", " ");
        if (normalized.length() <= TEXT_PREVIEW_LEN) {
            return normalized;
        }
        return normalized.substring(0, TEXT_PREVIEW_LEN) + "…";
    }
}
