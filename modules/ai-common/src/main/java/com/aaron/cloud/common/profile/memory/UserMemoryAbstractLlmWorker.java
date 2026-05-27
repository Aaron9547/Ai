package com.aaron.cloud.common.profile.memory;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.profile.TenUserMemoryAbstractRepository;
import com.aaron.cloud.common.profile.TenUserMemoryChunkRepository;
import com.aaron.cloud.common.profile.entity.TenUserMemoryAbstract;
import com.aaron.cloud.common.profile.entity.TenUserMemoryChunk;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 消费「最近 N 条记忆 chunk + 旧抽象 JSON」，调用<strong>当前租户语言模型</strong>生成<strong>严格 JSON</strong>并覆盖
 * {@code ten_user_memory_abstract.body_json}。策略对齐「工作记忆 / 情景 / 语义事实 / 画像 delta」分层维护思路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryAbstractLlmWorker {

    private final TenUserMemoryChunkRepository chunkRepository;
    private final TenUserMemoryAbstractRepository abstractRepository;
    private final SysLlmModelRepository sysLlmModelRepository;
    private final ModelInvokePort modelInvokePort;
    private final ObjectMapper objectMapper;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ObjectProvider<PromptTemplateResolvePort> promptTemplates;

    public void runRefresh(MemoryAbstractRefreshMessage msg) {
        if (msg == null || msg.getSubjectKey() == null || msg.getSubjectKey().isBlank()) {
            return;
        }
        long tenantId = msg.getTenantId();
        var memPol = tenantRuntimeSettingApplicationService.memoryPolicy(tenantId);
        if (!memPol.abstractRefreshEnabled()) {
            return;
        }
        String subjectKey = msg.getSubjectKey().trim();
        String alias = resolveModelAlias(msg);
        if (alias == null || alias.isBlank() || "mock".equalsIgnoreCase(alias.trim())) {
            log.debug("memory abstract refresh skipped (no model or mock) tenantId={} subject={}", tenantId, subjectKey);
            return;
        }
        SysLlmModel model =
                sysLlmModelRepository.findByTenantAndAlias(tenantId, alias.trim()).orElse(null);
        if (model == null) {
            log.warn("memory abstract refresh skipped model not found tenantId={} alias={}", tenantId, alias);
            return;
        }
        LlmModelKind k = model.getModelKind() != null ? model.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            log.warn("memory abstract refresh skipped non-language model tenantId={} alias={}", tenantId, alias);
            return;
        }
        int win = memPol.resolvedAbstractChunkWindow();
        List<TenUserMemoryChunk> recent = chunkRepository.listRecent(tenantId, subjectKey, win);
        String oldAbstract =
                abstractRepository
                        .findByTenantAndSubject(tenantId, subjectKey)
                        .map(TenUserMemoryAbstract::getBodyJson)
                        .orElse("");
        String chunkBlock = buildChunkTranscript(recent);
        String userPayload = resolveMemoryUserPayload(tenantId, subjectKey, oldAbstract, chunkBlock, msg.getTrigger());

        var turns = new ArrayList<ModelChatRequest.MessageTurn>();
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(resolveMemorySystem(tenantId));
        turns.add(sys);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(userPayload);
        turns.add(user);

        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(alias.trim());
        req.setThinkingEnabled(false);
        req.setMessages(turns);

        StringBuilder acc = new StringBuilder();
        try {
            modelInvokePort.streamCompletion(req, acc::append);
        } catch (Exception e) {
            log.error("memory abstract llm failed tenantId={} subject={}", tenantId, subjectKey, e);
            return;
        }
        String raw = acc.toString().strip();
        if (raw.isEmpty()) {
            return;
        }
        String json = stripCodeFence(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                log.warn("memory abstract refresh output not json object tenantId={}", tenantId);
                return;
            }
            String body = objectMapper.writeValueAsString(root);
            var existing = abstractRepository.findByTenantAndSubject(tenantId, subjectKey);
            if (existing.isEmpty()) {
                var n = new TenUserMemoryAbstract();
                n.setTenantId(tenantId);
                n.setSubjectKey(subjectKey);
                n.setBodyJson(body);
                abstractRepository.insert(n);
            } else {
                TenUserMemoryAbstract u = existing.get();
                u.setBodyJson(body);
                abstractRepository.updateById(u);
            }
            log.info("memory abstract refreshed tenantId={} subject={} trigger={}", tenantId, subjectKey, msg.getTrigger());
        } catch (Exception e) {
            log.warn("memory abstract json parse/persist failed tenantId={} subject={}", tenantId, subjectKey, e);
        }
    }

    private String resolveModelAlias(MemoryAbstractRefreshMessage msg) {
        if (msg.getModelAlias() != null && !msg.getModelAlias().isBlank()) {
            return msg.getModelAlias().trim();
        }
        var list = sysLlmModelRepository.listForCatalog(msg.getTenantId(), false);
        if (list.isEmpty()) {
            list = sysLlmModelRepository.listForCatalog(msg.getTenantId(), true);
        }
        return list.isEmpty() ? "" : list.getFirst().getAlias();
    }

    private String resolveMemorySystem(long tenantId) {
        PromptTemplateResolvePort port = promptTemplates.getIfAvailable();
        if (port == null) {
            return UserMemoryAbstractPrompts.SYSTEM;
        }
        String resolved = port.resolveSystem("memory_abstract_system", tenantId, "zh-CN");
        if (resolved == null || resolved.isBlank()) {
            return UserMemoryAbstractPrompts.SYSTEM;
        }
        return resolved;
    }

    private String resolveMemoryUserPayload(
            long tenantId, String subjectKey, String oldAbstract, String chunkBlock, String trigger) {
        PromptTemplateResolvePort port = promptTemplates.getIfAvailable();
        if (port == null) {
            return UserMemoryAbstractPrompts.userPayload(subjectKey, oldAbstract, chunkBlock, trigger);
        }
        String rendered =
                port.renderUser(
                        "memory_abstract_user",
                        tenantId,
                        "*",
                        Map.of(
                                "subject_key",
                                subjectKey == null ? "" : subjectKey,
                                "trigger",
                                trigger == null ? "" : trigger,
                                "old_abstract_json",
                                oldAbstract == null || oldAbstract.isBlank() ? "{}" : oldAbstract,
                                "chunk_transcript",
                                chunkBlock == null || chunkBlock.isBlank() ? "（无）" : chunkBlock));
        if (rendered == null || rendered.isBlank()) {
            return UserMemoryAbstractPrompts.userPayload(subjectKey, oldAbstract, chunkBlock, trigger);
        }
        return rendered;
    }

    private static String buildChunkTranscript(List<TenUserMemoryChunk> recent) {
        var lines = new ArrayList<String>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            TenUserMemoryChunk c = recent.get(i);
            String role = c.getChunkRole() == null ? "USER" : c.getChunkRole().trim();
            String sn = c.getContentSnippet() == null ? "" : c.getContentSnippet().trim();
            if (sn.isEmpty()) {
                continue;
            }
            lines.add("[" + role + "] " + sn);
        }
        return String.join("\n", lines);
    }

    private static String stripCodeFence(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence).strip();
            }
        }
        return s.strip();
    }
}
