package com.aaron.cloud.chat.rest.open;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aaron.cloud.chat.ChatApplicationService;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.common.context.TenantContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatConversationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatApplicationService chatApplicationService;

    @BeforeEach
    void tenant() {
        TenantContextHolder.set(
                TenantContextHolder.TenantSnapshot.builder()
                        .tenantId(1L)
                        .userId(null)
                        .deviceId("d1")
                        .build());
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void listMessagesAtOpenV1Path() throws Exception {
        when(chatApplicationService.listConversationMessages(eq(9L)))
                .thenReturn(
                        List.of(
                                new ChatMessageView(1L, "user", "hi", null, null, null, null, LocalDateTime.now(), null, null, null, null, null),
                                new ChatMessageView(
                                        2L,
                                        "assistant",
                                        "hello",
                                        null,
                                        1,
                                        2,
                                        3,
                                        LocalDateTime.now(),
                                        "m1",
                                        null,
                                        null,
                                        null,
                                        null)));
        mockMvc.perform(
                        get("/open/v1/chat/conversations/9/messages")
                                .header("X-Tenant-Id", "1")
                                .header("X-Device-Id", "d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].totalTokens").value(3))
                .andExpect(jsonPath("$[1].modelAlias").value("m1"));
    }
}
