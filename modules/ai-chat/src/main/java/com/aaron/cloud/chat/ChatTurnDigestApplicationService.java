package com.aaron.cloud.chat;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.chat.ChatMessageRole;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.util.TextClamp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 每轮助手落库后：生成 {@code meta.contentSummary} 供短期记忆与抽检展示。会话标题由 {@link ChatApplicationService} 在首条用户发送时同步
 * 更新，本服务不再改标题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTurnDigestApplicationService {

    public static final String META_CONTENT_SUMMARY = "contentSummary";

    private static final int ASSISTANT_INPUT_CLAMP = 24_000;
    private static final int SUMMARY_OUT_MAX = 420;
    private static final int TITLE_OUT_MAX = 36;

    private static final String DIGEST_SYSTEM =
            """
            你是对话归档助手。根据「用户问题」和「助手完整回复」，只输出严格 JSON（不要 markdown 代码块、不要多余说明），格式：
            {"contentSummary":"...","conversationTitle":"..."}

            约束：
            - contentSummary：中文，1～3 句，抓住要点，供后续轮次作上下文压缩占位，不超过 420 字。
            - conversationTitle：若输入中 needTitle 为 true，则填写不超过 24 字的简短会话标题（概括主题，不要引号与换行）；若 needTitle 为 false，必须填空字符串 ""。
            """;

    private final ChatMessageRepository messageRepository;
    private final ChatConversationRepository conversationRepository;
    private final ModelInvokePort modelInvokePort;
    private final ObjectMapper objectMapper;
    private final SysLlmModelRepository sysLlmModelRepository;

    public void scheduleTurnDigest(
            TenantContextHolder.TenantSnapshot snap,
            long conversationId,
            long assistantMessageId,
            String userQuestionPlain,
            String assistantFullText,
            String modelAlias,
            boolean isMock) {
        Thread.startVirtualThread(
                () -> {
                    try {
                        runTurnDigest(
                                snap,
                                conversationId,
                                assistantMessageId,
                                userQuestionPlain,
                                assistantFullText,
                                modelAlias,
                                isMock);
                    } catch (Exception ex) {
                        log.warn(
                                "[对话摘要] 回合摘要生成失败：租户 {}，会话 {}，助手消息 {}",
                                snap.getTenantId(),
                                conversationId,
                                assistantMessageId,
                                ex);
                    }
                });
    }

    private void runTurnDigest(
            TenantContextHolder.TenantSnapshot snap,
            long conversationId,
            long assistantMessageId,
            String userQuestionPlain,
            String assistantFullText,
            String modelAliasRaw,
            boolean isMock) {
        long tenantId = snap.getTenantId();
        Optional<ChatMessage> asstOpt = messageRepository.findById(assistantMessageId, tenantId);
        if (asstOpt.isEmpty() || asstOpt.get().getRole() != ChatMessageRole.ASSISTANT) {
            return;
        }
        String assistant = assistantFullText == null ? "" : assistantFullText.trim();
        if (assistant.isEmpty()) {
            return;
        }
        String userQ = userQuestionPlain == null ? "" : userQuestionPlain.trim();
        boolean needTitle = false;

        String summary;
        String titleCandidate = "";
        boolean useLlm =
                !isMock
                        && modelAliasRaw != null
                        && !modelAliasRaw.isBlank()
                        && !"mock".equalsIgnoreCase(modelAliasRaw.trim());
        if (!useLlm) {
            summary = TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
            if (needTitle) {
                titleCandidate = fallbackTitleFromAssistant(assistant);
            }
        } else {
            String alias = modelAliasRaw.trim();
            SysLlmModel model = sysLlmModelRepository.findByTenantAndAlias(tenantId, alias).orElse(null);
            if (model == null) {
                summary = TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
                if (needTitle) {
                    titleCandidate = fallbackTitleFromAssistant(assistant);
                }
            } else {
                LlmModelKind k = model.getModelKind() != null ? model.getModelKind() : LlmModelKind.LANGUAGE;
                if (k != LlmModelKind.LANGUAGE) {
                    summary = TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
                    if (needTitle) {
                        titleCandidate = fallbackTitleFromAssistant(assistant);
                    }
                } else {
                    try {
                        LlmModelKindPolicy.assertLanguageModelForChatStream(model);
                    } catch (Exception ex) {
                        log.debug("[对话摘要] 跳过非对话语言模型：{}，租户 {}", alias, tenantId);
                        summary = TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
                        if (needTitle) {
                            titleCandidate = fallbackTitleFromAssistant(assistant);
                        }
                        persistSummaryAndMaybeTitle(
                                tenantId, conversationId, assistantMessageId, summary, needTitle, titleCandidate);
                        return;
                    }
                    String userPayload =
                            "needTitle="
                                    + needTitle
                                    + "\n\n【用户问题】\n"
                                    + userQ
                                    + "\n\n【助手完整回复】\n"
                                    + TextClamp.ellipsis(assistant, ASSISTANT_INPUT_CLAMP);
                    var turns = new ArrayList<ModelChatRequest.MessageTurn>();
                    var sys = new ModelChatRequest.MessageTurn();
                    sys.setRole("system");
                    sys.setContent(DIGEST_SYSTEM);
                    turns.add(sys);
                    var user = new ModelChatRequest.MessageTurn();
                    user.setRole("user");
                    user.setContent(userPayload);
                    turns.add(user);
                    var req = new ModelChatRequest();
                    req.setTenantId(tenantId);
                    req.setUserId(snap.getUserId());
                    req.setDeviceId(snap.getDeviceId());
                    req.setModelAlias(alias);
                    req.setThinkingEnabled(false);
                    req.setMessages(turns);
                    StringBuilder acc = new StringBuilder();
                    try {
                        modelInvokePort.streamCompletion(req, acc::append);
                    } catch (Exception e) {
                        log.warn("[对话摘要] 调用大模型生成摘要失败：租户 {}，会话 {}", tenantId, conversationId, e);
                        summary = TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
                        if (needTitle) {
                            titleCandidate = fallbackTitleFromAssistant(assistant);
                        }
                        persistSummaryAndMaybeTitle(
                                tenantId, conversationId, assistantMessageId, summary, needTitle, titleCandidate);
                        return;
                    }
                    ParsedDigest parsed = parseDigestJson(stripCodeFence(acc.toString()));
                    summary =
                            parsed.contentSummary() != null && !parsed.contentSummary().isBlank()
                                    ? TextClamp.ellipsis(parsed.contentSummary().trim(), SUMMARY_OUT_MAX)
                                    : TextClamp.ellipsis(assistant, SUMMARY_OUT_MAX);
                    if (needTitle) {
                        titleCandidate =
                                parsed.conversationTitle() != null && !parsed.conversationTitle().isBlank()
                                        ? TextClamp.ellipsis(parsed.conversationTitle().trim(), TITLE_OUT_MAX)
                                        : fallbackTitleFromAssistant(assistant);
                    }
                }
            }
        }
        persistSummaryAndMaybeTitle(tenantId, conversationId, assistantMessageId, summary, needTitle, titleCandidate);
    }

    private void persistSummaryAndMaybeTitle(
            long tenantId,
            long conversationId,
            long assistantMessageId,
            String summary,
            boolean needTitle,
            String titleCandidate) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        ChatMessage latest =
                messageRepository.findById(assistantMessageId, tenantId).orElse(null);
        if (latest == null || latest.getRole() != ChatMessageRole.ASSISTANT) {
            return;
        }
        ObjectNode root = readObjectMeta(latest.getMetaJson());
        root.put(META_CONTENT_SUMMARY, summary);
        try {
            messageRepository.updateMetaJson(assistantMessageId, tenantId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("[对话摘要] 写入消息摘要元数据失败：消息 {}", assistantMessageId, e);
            return;
        }
        if (needTitle && titleCandidate != null && !titleCandidate.isBlank()) {
            Optional<ChatConversation> conv = conversationRepository.findById(conversationId, tenantId);
            if (conv.isPresent() && isPlaceholderConversationTitle(conv.get().getTitle())) {
                conversationRepository.updateTitle(conversationId, tenantId, titleCandidate);
            }
        }
    }

    private ObjectNode readObjectMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode tree = objectMapper.readTree(metaJson);
            if (tree instanceof ObjectNode on) {
                return on.deepCopy();
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private static boolean isPlaceholderConversationTitle(String title) {
        if (title == null) {
            return true;
        }
        String t = title.trim();
        if (t.isEmpty()) {
            return true;
        }
        if ("新会话".equals(t)) {
            return true;
        }
        return t.startsWith("新对话");
    }

    private static String fallbackTitleFromAssistant(String assistant) {
        String normalized = assistant.replace('\r', '\n');
        int nl = normalized.indexOf('\n');
        String firstLine = (nl >= 0 ? normalized.substring(0, nl) : normalized).trim();
        if (firstLine.isEmpty()) {
            return "新会话";
        }
        return TextClamp.ellipsis(firstLine, TITLE_OUT_MAX);
    }

    private ParsedDigest parseDigestJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedDigest("", "");
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (!n.isObject()) {
                return new ParsedDigest("", "");
            }
            String cs = n.path("contentSummary").asText("");
            String ct = n.path("conversationTitle").asText("");
            return new ParsedDigest(cs, ct);
        } catch (Exception e) {
            return new ParsedDigest("", "");
        }
    }

    private record ParsedDigest(String contentSummary, String conversationTitle) {}

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
