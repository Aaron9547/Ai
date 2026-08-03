package com.aaron.cloud.chat.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderParseLlmServiceTest {

    @Mock
    private ModelInvokePort modelInvokePort;

    @Mock
    private SysLlmModelRepository llmModelRepository;

    @Test
    void parse_validModelJson_returnsCreate() throws Exception {
        ReminderParseLlmService reminderParseLlmService =
                new ReminderParseLlmService(modelInvokePort, llmModelRepository, new ObjectMapper());
        String llmJson =
                """
                {
                  "contractVersion": 1,
                  "op": "CREATE",
                  "title": "喝水",
                  "scheduleType": "DAILY",
                  "cronExpression": "0 0 8 * * ?",
                  "userMessage": "已设置每天8点提醒",
                  "error": null
                }
                """;
        SysLlmModel model = new SysLlmModel();
        model.setAlias("gpt-test");
        model.setModelKind(LlmModelKind.LANGUAGE);
        when(llmModelRepository.pickDefaultLanguageModel(anyLong())).thenReturn(Optional.of(model));
        doAnswer(
                        inv -> {
                            var onChunk = inv.getArgument(1, java.util.function.Consumer.class);
                            onChunk.accept(llmJson);
                            return null;
                        })
                .when(modelInvokePort)
                .streamCompletion(any(), any());

        ReminderParseRequest req =
                new ReminderParseRequest(
                        ReminderParseContracts.CONTRACT_VERSION,
                        "CREATE",
                        "每天8点提醒我喝水",
                        1L,
                        10L,
                        "zh-CN",
                        0,
                        null);
        var out = reminderParseLlmService.parse(req, null);
        assertEquals("CREATE", out.op());
        assertEquals("喝水", out.title());
        assertNull(out.error());
    }
}
