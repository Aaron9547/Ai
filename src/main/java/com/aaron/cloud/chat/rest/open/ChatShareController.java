package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.ChatConversationShareService;
import com.aaron.cloud.chat.dto.ChatShareCreateView;
import com.aaron.cloud.chat.dto.ChatSharePublicView;
import com.aaron.cloud.chat.dto.CreateChatShareRequest;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatShareController extends OpenV1ControllerBases.Chat {

    private final ChatConversationShareService chatConversationShareService;

    @PostMapping("/conversations/{id}/shares")
    public ChatShareCreateView createShare(
            @PathVariable("id") long conversationId, @Valid @RequestBody CreateChatShareRequest body) {
        return chatConversationShareService.createShare(conversationId, body);
    }

    @GetMapping("/shares/{code}")
    public ChatSharePublicView getShare(@PathVariable("code") String code) {
        return chatConversationShareService.getPublicShare(code);
    }
}
