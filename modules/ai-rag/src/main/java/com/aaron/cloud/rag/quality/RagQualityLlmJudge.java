package com.aaron.cloud.rag.quality;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagQualityLlmJudge {

    private final ModelInvokePort modelInvokePort;

    public String invoke(long tenantId, SysLlmModel model, String system, String user, String usageScene)
            throws Exception {
        var sysTurn = new ModelChatRequest.MessageTurn();
        sysTurn.setRole("system");
        sysTurn.setContent(system);
        var userTurn = new ModelChatRequest.MessageTurn();
        userTurn.setRole("user");
        userTurn.setContent(user);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(model.getAlias());
        req.setThinkingEnabled(false);
        req.setUsageScene(usageScene);
        req.setMessages(List.of(sysTurn, userTurn));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString().trim();
    }
}
