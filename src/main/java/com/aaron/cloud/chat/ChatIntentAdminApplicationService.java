package com.aaron.cloud.chat;

import com.aaron.cloud.chat.dto.ChatIntentAdminDtos;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.chat.ChatIntentDefinitionRepository;
import com.aaron.cloud.common.chat.ChatIntentKeywordRepository;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatIntentAdminApplicationService {

    private final ChatIntentDefinitionRepository definitionRepository;
    private final ChatIntentKeywordRepository keywordRepository;

    public List<ChatIntentAdminDtos.IntentRow> listDefinitions(Long filterTenantId) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        return definitionRepository.listForAdmin(tid).stream().map(this::toIntentRow).toList();
    }

    public ChatIntentAdminDtos.IntentRow createDefinition(ChatIntentAdminDtos.IntentCreateBody body) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(body.targetTenantId());
        String code = body.code().trim();
        if (definitionRepository.findByTenantAndCode(dataTenantId, code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该租户下意图编码已存在");
        }
        var row = new ChatIntentDefinition();
        row.setTenantId(dataTenantId);
        row.setCode(code);
        row.setDisplayName(body.displayName().trim());
        row.setDescription(body.description() == null ? null : body.description().trim());
        row.setHandlerKind(body.handlerKind());
        row.setEnabled(body.enabled());
        row.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        row.setExtraConfigJson(body.extraConfigJson());
        definitionRepository.insert(row);
        return toIntentRow(
                definitionRepository
                        .findById(row.getId(), dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    public ChatIntentAdminDtos.IntentRow updateDefinition(long id, ChatIntentAdminDtos.IntentUpdateBody body) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(body.targetTenantId());
        ChatIntentDefinition row =
                definitionRepository
                        .findById(id, dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "意图不存在"));
        if (body.displayName() != null) {
            row.setDisplayName(body.displayName().trim());
        }
        if (body.description() != null) {
            row.setDescription(body.description().trim());
        }
        if (body.handlerKind() != null) {
            row.setHandlerKind(body.handlerKind());
        }
        if (body.enabled() != null) {
            row.setEnabled(body.enabled());
        }
        if (body.sortOrder() != null) {
            row.setSortOrder(body.sortOrder());
        }
        if (body.extraConfigJson() != null) {
            row.setExtraConfigJson(body.extraConfigJson());
        }
        definitionRepository.updateById(row, dataTenantId);
        return toIntentRow(
                definitionRepository
                        .findById(id, dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public void deleteDefinition(long id, Long targetTenantId) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
        if (definitionRepository.findById(id, dataTenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "意图不存在");
        }
        keywordRepository.deleteByIntent(dataTenantId, id);
        definitionRepository.deleteById(id, dataTenantId);
    }

    public List<ChatIntentAdminDtos.KeywordRow> listKeywords(long intentId, Long targetTenantId) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
        assertIntentInTenant(intentId, dataTenantId);
        return keywordRepository.listByIntent(dataTenantId, intentId).stream().map(this::toKeywordRow).toList();
    }

    public ChatIntentAdminDtos.KeywordRow addKeyword(
            long intentId, Long targetTenantId, ChatIntentAdminDtos.KeywordCreateBody body) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
        assertIntentInTenant(intentId, dataTenantId);
        var row = new ChatIntentKeyword();
        row.setTenantId(dataTenantId);
        row.setIntentId(intentId);
        row.setPhrase(body.phrase().trim());
        row.setKeywordKind(body.keywordKind());
        row.setEnabled(body.enabled());
        row.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        keywordRepository.insert(row);
        return toKeywordRow(
                keywordRepository
                        .findById(row.getId(), dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    public ChatIntentAdminDtos.KeywordRow updateKeyword(
            long intentId, long keywordId, ChatIntentAdminDtos.KeywordUpdateBody body, Long targetTenantId) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
        assertIntentInTenant(intentId, dataTenantId);
        ChatIntentKeyword row =
                keywordRepository
                        .findById(keywordId, dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "关键词不存在"));
        if (!row.getIntentId().equals(intentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关键词不属于该意图");
        }
        if (body.phrase() != null) {
            row.setPhrase(body.phrase().trim());
        }
        if (body.keywordKind() != null) {
            row.setKeywordKind(body.keywordKind());
        }
        if (body.enabled() != null) {
            row.setEnabled(body.enabled());
        }
        if (body.sortOrder() != null) {
            row.setSortOrder(body.sortOrder());
        }
        keywordRepository.updateById(row, dataTenantId);
        return toKeywordRow(
                keywordRepository
                        .findById(keywordId, dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public void deleteKeyword(long intentId, long keywordId, Long targetTenantId) {
        long dataTenantId = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
        assertIntentInTenant(intentId, dataTenantId);
        ChatIntentKeyword row =
                keywordRepository
                        .findById(keywordId, dataTenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!row.getIntentId().equals(intentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关键词不属于该意图");
        }
        keywordRepository.deleteById(keywordId, dataTenantId);
    }

    private void assertIntentInTenant(long intentId, long dataTenantId) {
        if (definitionRepository.findById(intentId, dataTenantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "意图不存在");
        }
    }

    private ChatIntentAdminDtos.IntentRow toIntentRow(ChatIntentDefinition d) {
        return new ChatIntentAdminDtos.IntentRow(
                d.getId(),
                d.getTenantId(),
                d.getCode(),
                d.getDisplayName(),
                d.getDescription(),
                d.getHandlerKind(),
                d.getEnabled() == null ? ToggleState.OFF : d.getEnabled(),
                d.getSortOrder() == null ? 0 : d.getSortOrder(),
                d.getExtraConfigJson());
    }

    private ChatIntentAdminDtos.KeywordRow toKeywordRow(ChatIntentKeyword k) {
        return new ChatIntentAdminDtos.KeywordRow(
                k.getId(),
                k.getIntentId(),
                k.getPhrase(),
                k.getKeywordKind(),
                k.getEnabled() == null ? ToggleState.OFF : k.getEnabled(),
                k.getSortOrder() == null ? 0 : k.getSortOrder());
    }
}
