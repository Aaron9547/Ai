package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.common.api.enums.ChatStarterDailyBatchStatus;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.api.enums.ChatStarterPromptSource;
import com.aaron.cloud.common.chat.ChatStarterDailyBatchRepository;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterDailyBatch;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.time.BeijingTime;
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

    public List<ChatStarterPromptDtos.PromptRow> listPrompts() {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        return promptRepository.listByTenant(tid).stream().map(this::toRow).toList();
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
                BeijingTime.formatDisplay(p.getCreatedAt()),
                BeijingTime.formatDisplay(p.getUpdatedAt()));
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
