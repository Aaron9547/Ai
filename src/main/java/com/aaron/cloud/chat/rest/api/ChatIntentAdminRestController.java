package com.aaron.cloud.chat.rest.api;

import com.aaron.cloud.chat.ChatIntentAdminApplicationService;
import com.aaron.cloud.chat.dto.ChatIntentAdminDtos;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatIntentAdminRestController extends ApiV1ControllerBases.AdminChat {

    private final ChatIntentAdminApplicationService chatIntentAdminApplicationService;

    @GetMapping("/intents")
    public List<ChatIntentAdminDtos.IntentRow> listIntents(@RequestParam(required = false) Long filterTenantId) {
        return chatIntentAdminApplicationService.listDefinitions(filterTenantId);
    }

    @PostMapping("/intents")
    public ChatIntentAdminDtos.IntentRow createIntent(@Valid @RequestBody ChatIntentAdminDtos.IntentCreateBody body) {
        return chatIntentAdminApplicationService.createDefinition(body);
    }

    @PutMapping("/intents/{id}")
    public ChatIntentAdminDtos.IntentRow updateIntent(
            @PathVariable("id") long id, @Valid @RequestBody ChatIntentAdminDtos.IntentUpdateBody body) {
        return chatIntentAdminApplicationService.updateDefinition(id, body);
    }

    @DeleteMapping("/intents/{id}")
    public void deleteIntent(@PathVariable("id") long id, @RequestParam(required = false) Long targetTenantId) {
        chatIntentAdminApplicationService.deleteDefinition(id, targetTenantId);
    }

    @GetMapping("/intents/{intentId}/keywords")
    public List<ChatIntentAdminDtos.KeywordRow> listKeywords(
            @PathVariable("intentId") long intentId, @RequestParam(required = false) Long targetTenantId) {
        return chatIntentAdminApplicationService.listKeywords(intentId, targetTenantId);
    }

    @PostMapping("/intents/{intentId}/keywords")
    public ChatIntentAdminDtos.KeywordRow addKeyword(
            @PathVariable("intentId") long intentId,
            @RequestParam(required = false) Long targetTenantId,
            @Valid @RequestBody ChatIntentAdminDtos.KeywordCreateBody body) {
        return chatIntentAdminApplicationService.addKeyword(intentId, targetTenantId, body);
    }

    @PutMapping("/intents/{intentId}/keywords/{keywordId}")
    public ChatIntentAdminDtos.KeywordRow updateKeyword(
            @PathVariable("intentId") long intentId,
            @PathVariable("keywordId") long keywordId,
            @RequestParam(required = false) Long targetTenantId,
            @Valid @RequestBody ChatIntentAdminDtos.KeywordUpdateBody body) {
        return chatIntentAdminApplicationService.updateKeyword(intentId, keywordId, body, targetTenantId);
    }

    @DeleteMapping("/intents/{intentId}/keywords/{keywordId}")
    public void deleteKeyword(
            @PathVariable("intentId") long intentId,
            @PathVariable("keywordId") long keywordId,
            @RequestParam(required = false) Long targetTenantId) {
        chatIntentAdminApplicationService.deleteKeyword(intentId, keywordId, targetTenantId);
    }
}
