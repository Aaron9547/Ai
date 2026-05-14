package com.aaron.cloud.chat.rest.api;

import com.aaron.cloud.chat.ChatIntentAdminApplicationService;
import com.aaron.cloud.chat.dto.ChatIntentAdminDtos;
import com.aaron.cloud.chat.dto.ChatIntentAdminDtos.IntentHandlerKindOption;
import com.aaron.cloud.chat.intent.spi.IntentHandlerPluginRegistry;
import com.aaron.cloud.common.api.dto.IntentHandlerConfigFieldMeta;
import com.aaron.cloud.common.api.enums.ChatIntentHandlerKind;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ChatIntentAdminRestController extends ApiV1ControllerBases.AdminChat {

    private final ChatIntentAdminApplicationService chatIntentAdminApplicationService;
    private final IntentHandlerPluginRegistry intentHandlerPluginRegistry;

    @GetMapping("/intents")
    public List<ChatIntentAdminDtos.IntentRow> listIntents() {
        return chatIntentAdminApplicationService.listDefinitions();
    }

    /** 管理端处理器下拉：仅返回已注册 Spring Bean 的类型，文案来自 {@link com.aaron.cloud.common.api.enums.ChatIntentHandlerKind}。 */
    @GetMapping("/intent-handler-kinds")
    public List<IntentHandlerKindOption> listIntentHandlerKinds() {
        return intentHandlerPluginRegistry.listRegisteredKindOptions();
    }

    /**
     * 管理端按处理器类型拉取 {@code extra_config_json.handlerParams} 动态表单元数据；无实现时 404。
     */
    @GetMapping("/intent-handlers/{kind}/config-schema")
    public List<IntentHandlerConfigFieldMeta> intentHandlerConfigSchema(@PathVariable("kind") String kind) {
        ChatIntentHandlerKind k;
        try {
            k = ChatIntentHandlerKind.valueOf(kind);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown handler kind: " + kind, ex);
        }
        return intentHandlerPluginRegistry
                .get(k)
                .map(p -> p.configSchema())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no plugin for kind " + kind));
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
    public void deleteIntent(@PathVariable("id") long id) {
        chatIntentAdminApplicationService.deleteDefinition(id);
    }

    @GetMapping("/intents/{intentId}/keywords")
    public List<ChatIntentAdminDtos.KeywordRow> listKeywords(@PathVariable("intentId") long intentId) {
        return chatIntentAdminApplicationService.listKeywords(intentId);
    }

    @PostMapping("/intents/{intentId}/keywords")
    public ChatIntentAdminDtos.KeywordRow addKeyword(
            @PathVariable("intentId") long intentId, @Valid @RequestBody ChatIntentAdminDtos.KeywordCreateBody body) {
        return chatIntentAdminApplicationService.addKeyword(intentId, body);
    }

    @PutMapping("/intents/{intentId}/keywords/{keywordId}")
    public ChatIntentAdminDtos.KeywordRow updateKeyword(
            @PathVariable("intentId") long intentId,
            @PathVariable("keywordId") long keywordId,
            @Valid @RequestBody ChatIntentAdminDtos.KeywordUpdateBody body) {
        return chatIntentAdminApplicationService.updateKeyword(intentId, keywordId, body);
    }

    @DeleteMapping("/intents/{intentId}/keywords/{keywordId}")
    public void deleteKeyword(@PathVariable("intentId") long intentId, @PathVariable("keywordId") long keywordId) {
        chatIntentAdminApplicationService.deleteKeyword(intentId, keywordId);
    }
}
