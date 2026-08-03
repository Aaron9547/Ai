package com.aaron.cloud.chat.knowledgeplanet;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgePlanetLlmSupport {

    private final SysLlmModelRepository llmModelRepository;
    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final ModelInvokePort modelInvokePort;
    private final ObjectMapper objectMapper;

    public Optional<SysLlmModel> resolveLanguageModel(long tenantId, String modelAliasOrNull) {
        if (modelAliasOrNull != null && !modelAliasOrNull.isBlank() && !"mock".equalsIgnoreCase(modelAliasOrNull.trim())) {
            Optional<SysLlmModel> byAlias =
                    llmModelRepository.findByTenantAndAlias(tenantId, modelAliasOrNull.trim());
            if (byAlias.isPresent() && byAlias.get().getModelKind() == LlmModelKind.LANGUAGE) {
                return byAlias;
            }
        }
        Optional<Long> configured = planetRuntime.digestModelId(tenantId);
        if (configured.isPresent()) {
            Optional<SysLlmModel> byId = llmModelRepository.findById(tenantId, configured.get());
            if (byId.isPresent() && byId.get().getModelKind() == LlmModelKind.LANGUAGE) {
                return byId;
            }
        }
        return llmModelRepository.pickDefaultLanguageModel(tenantId);
    }

    public String invokeJson(long tenantId, SysLlmModel model, String systemPrompt, String userContent)
            throws Exception {
        LlmModelKindPolicy.assertLanguageModelForChatStream(model);
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(systemPrompt);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(userContent);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(model.getAlias());
        req.setThinkingEnabled(false);
        req.setUsageScene(LlmUsageScene.KNOWLEDGE_PLANET.getCode());
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString().trim();
    }

    public <T> T parseJson(String raw, Class<T> type) throws Exception {
        String json = extractJsonObject(raw);
        return objectMapper.readValue(json, type);
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String t = raw.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        int aStart = t.indexOf('[');
        int aEnd = t.lastIndexOf(']');
        if (aStart >= 0 && aEnd > aStart) {
            return t.substring(aStart, aEnd + 1);
        }
        return t;
    }
}
