package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.enums.chat.ChatStarterDailyBatchStatus;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import com.aaron.cloud.common.chat.ChatStarterDailyBatchRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterDailyBatch;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.time.BeijingTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatStarterPromptAdminApplicationService {

    private final ChatStarterPromptRepository promptRepository;
    private final ChatStarterDailyBatchRepository dailyBatchRepository;
    private final ChatStarterDailyHotTopicService dailyHotTopicService;
    private final ChatStarterPromptJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public ChatStarterPromptDtos.PromptPageResult pagePrompts(
            String sceneCode, String sourceCode, long page, long size) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        long pageNo = Math.max(1, page);
        long pageSize = Math.min(100, Math.max(1, size));
        ChatStarterPromptScene scene;
        try {
            scene = ChatStarterPromptScene.fromCode(sceneCode);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scene 无效");
        }
        ChatStarterPromptSource source = null;
        if (sourceCode != null && !sourceCode.isBlank()) {
            try {
                source = ChatStarterPromptSource.fromCode(sourceCode);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 无效");
            }
        }
        var p = promptRepository.pageByTenant(tid, scene, source, pageNo, pageSize);
        List<ChatStarterPromptDtos.PromptRow> rows =
                p.getRecords().stream().map(this::toRow).toList();
        return new ChatStarterPromptDtos.PromptPageResult(rows, p.getTotal(), pageNo, pageSize);
    }

    public ChatStarterPromptDtos.PromptRow create(ChatStarterPromptDtos.PromptCreateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        var row = new ChatStarterPrompt();
        row.setTenantId(tid);
        row.setScene(ChatStarterPromptScene.fromCode(body.scene()));
        row.setSource(ChatStarterPromptSource.MANUAL);
        row.setPromptText(body.promptText().trim());
        row.setWeight(body.weight() == null ? 100 : body.weight());
        row.setEnabled(Boolean.FALSE.equals(body.enabled()) ? 0 : 1);
        row.setRequireThinking(boolToTiny(body.requireThinking()));
        row.setRequireWebSearch(boolToTiny(body.requireWebSearch()));
        row.setValidFrom(body.validFrom());
        row.setValidUntil(body.validUntil());
        row.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        promptRepository.insert(row);
        return toRow(
                promptRepository
                        .findById(row.getId(), tid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    public ChatStarterPromptDtos.PromptRow update(long id, ChatStarterPromptDtos.PromptUpdateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        ChatStarterPrompt row =
                promptRepository
                        .findById(id, tid)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐问题不存在"));
        if (body.promptText() != null) {
            row.setPromptText(body.promptText().trim());
        }
        if (body.weight() != null) {
            row.setWeight(body.weight());
        }
        if (body.enabled() != null) {
            row.setEnabled(body.enabled() ? 1 : 0);
        }
        if (body.requireThinking() != null) {
            row.setRequireThinking(boolToTiny(body.requireThinking()));
        }
        if (body.requireWebSearch() != null) {
            row.setRequireWebSearch(boolToTiny(body.requireWebSearch()));
        }
        if (body.validFrom() != null) {
            row.setValidFrom(body.validFrom());
        }
        if (body.validUntil() != null) {
            row.setValidUntil(body.validUntil());
        }
        if (body.sortOrder() != null) {
            row.setSortOrder(body.sortOrder());
        }
        promptRepository.updateById(row, tid);
        return toRow(
                promptRepository
                        .findById(id, tid)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public void delete(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        if (promptRepository.findById(id, tid).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐问题不存在");
        }
        promptRepository.deleteById(id, tid);
    }

    /** 联网知识库：查看某条沉淀的摘要与引用列表（scene=WEB_KNOWLEDGE）。 */
    public ChatStarterPromptDtos.WebGroundingDetailView getWebGroundingDetail(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        ChatStarterPrompt row =
                promptRepository
                        .findById(id, tid)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐问题不存在"));
        if (row.getScene() != ChatStarterPromptScene.WEB_KNOWLEDGE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "非联网知识库条目");
        }
        return parseWebGroundingDetail(row);
    }

    public List<ChatStarterPromptDtos.DailyBatchRow> listDailyBatches(int limit) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        return dailyBatchRepository.listByTenant(tid, limit).stream().map(this::toBatchRow).toList();
    }

    public ChatStarterPromptDtos.RefreshDailyHotResult refreshDailyHot(boolean force) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        dailyHotTopicService.refreshForTenant(tid, force);
        var batch =
                dailyBatchRepository
                        .findByTenantAndDate(tid, BeijingTime.today())
                        .orElse(null);
        if (batch == null) {
            return new ChatStarterPromptDtos.RefreshDailyHotResult(false, "未生成批次", 0);
        }
        if (batch.getStatus() == ChatStarterDailyBatchStatus.OK) {
            int n = jsonSupport.parseQuestions(batch.getQuestionsJson()).size();
            return new ChatStarterPromptDtos.RefreshDailyHotResult(true, "ok", n);
        }
        return new ChatStarterPromptDtos.RefreshDailyHotResult(
                false, batch.getErrorMessage() == null ? "failed" : batch.getErrorMessage(), 0);
    }

    private ChatStarterPromptDtos.PromptRow toRow(ChatStarterPrompt p) {
        String grounding = p.getGroundingJson();
        return new ChatStarterPromptDtos.PromptRow(
                p.getId(),
                p.getScene() == null ? null : p.getScene().getCode(),
                p.getSource() == null ? null : p.getSource().getCode(),
                p.getPromptText(),
                p.getWeight() == null ? 0 : p.getWeight(),
                p.getEnabled() != null && p.getEnabled() == 1,
                p.getRequireThinking() != null && p.getRequireThinking() == 1,
                p.getRequireWebSearch() != null && p.getRequireWebSearch() == 1,
                p.getValidFrom(),
                p.getValidUntil(),
                p.getSortOrder() == null ? 0 : p.getSortOrder(),
                p.getBatchKey(),
                p.getQueryNormalized(),
                p.getHitCount() == null ? 0 : p.getHitCount(),
                referenceCount(grounding),
                groundingSummaryPreview(grounding),
                BeijingTime.formatDisplay(p.getCreatedAt()),
                BeijingTime.formatDisplay(p.getUpdatedAt()));
    }

    private int referenceCount(String groundingJson) {
        if (groundingJson == null || groundingJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(groundingJson);
            JsonNode refs = root.path("references");
            return refs.isArray() ? refs.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String groundingSummaryPreview(String groundingJson) {
        if (groundingJson == null || groundingJson.isBlank()) {
            return "";
        }
        try {
            String s = objectMapper.readTree(groundingJson).path("summaryText").asText("").trim();
            if (s.length() <= 120) {
                return s;
            }
            return s.substring(0, 120) + "…";
        } catch (Exception e) {
            return "";
        }
    }

    private ChatStarterPromptDtos.WebGroundingDetailView parseWebGroundingDetail(ChatStarterPrompt row) {
        String grounding = row.getGroundingJson();
        String summary = "";
        List<ChatStarterPromptDtos.WebGroundingReferenceItem> refs = new ArrayList<>();
        if (grounding != null && !grounding.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(grounding);
                summary = root.path("summaryText").asText("").trim();
                JsonNode arr = root.path("references");
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        refs.add(
                                new ChatStarterPromptDtos.WebGroundingReferenceItem(
                                        n.path("title").asText(""),
                                        n.path("url").asText(""),
                                        n.path("snippet").asText(""),
                                        textOrNull(n, "siteName"),
                                        textOrNull(n, "publishTime"),
                                        textOrNull(n, "sourceKey")));
                    }
                }
            } catch (Exception ignored) {
                // leave empty
            }
        }
        return new ChatStarterPromptDtos.WebGroundingDetailView(
                row.getPromptText() == null ? "" : row.getPromptText(),
                row.getQueryNormalized() == null ? "" : row.getQueryNormalized(),
                summary,
                List.copyOf(refs));
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private ChatStarterPromptDtos.DailyBatchRow toBatchRow(ChatStarterDailyBatch b) {
        return new ChatStarterPromptDtos.DailyBatchRow(
                b.getId(),
                b.getTopicDate() == null ? null : b.getTopicDate().toString(),
                b.getStatus() == null ? null : b.getStatus().getCode(),
                jsonSupport.parseQuestions(b.getQuestionsJson()),
                b.getErrorMessage(),
                BeijingTime.formatDisplay(b.getFetchedAt()));
    }

    private static Integer boolToTiny(Boolean v) {
        if (v == null) {
            return null;
        }
        return v ? 1 : 0;
    }
}
