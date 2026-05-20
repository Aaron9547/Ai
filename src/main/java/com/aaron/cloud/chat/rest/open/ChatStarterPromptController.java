package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.chat.starter.ChatStarterFollowUpService;
import com.aaron.cloud.chat.starter.ChatStarterPromptApplicationService;
import com.aaron.cloud.common.api.enums.ChatStarterEventType;
import com.aaron.cloud.common.api.enums.ChatStarterPromptScene;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatStarterPromptController extends OpenV1ControllerBases.Chat {

    private final ChatStarterPromptApplicationService starterPromptApplicationService;
    private final ChatStarterFollowUpService followUpService;

    @GetMapping("/starter-prompts")
    public ChatStarterPromptDtos.StarterPromptListView listStarterPrompts(
            @RequestParam(defaultValue = "EMPTY") String scene,
            @RequestParam(defaultValue = "6") int limit,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(required = false) List<Long> excludeIds,
            @RequestParam(defaultValue = "false") boolean thinkingEnabled,
            @RequestParam(defaultValue = "false") boolean webSearchEnabled) {
        return starterPromptApplicationService.listForOpen(
                ChatStarterPromptScene.fromCode(scene),
                limit,
                refresh,
                ChatStarterPromptApplicationService.parseExcludeIds(excludeIds),
                thinkingEnabled,
                webSearchEnabled);
    }

    @PostMapping("/starter-prompts/events")
    public void recordEvent(@Valid @RequestBody ChatStarterPromptDtos.StarterEventBody body) {
        starterPromptApplicationService.recordEvent(
                body.promptId(),
                ChatStarterPromptScene.fromCode(body.scene()),
                ChatStarterEventType.fromCode(body.eventType()));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/follow-up-prompts")
    public ChatStarterPromptDtos.StarterPromptListView listFollowUpPrompts(
            @PathVariable long conversationId,
            @PathVariable long messageId,
            @RequestParam(defaultValue = "3") int limit) {
        return followUpService.listFollowUp(conversationId, messageId, limit);
    }
}
