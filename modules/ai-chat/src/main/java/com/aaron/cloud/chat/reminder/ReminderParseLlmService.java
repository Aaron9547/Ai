package com.aaron.cloud.chat.reminder;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderParseLlmService {

    private static final String SYSTEM_PROMPT =
            """
            你是「一句话邮件提醒」话术解析器。根据用户中文输入，输出唯一 JSON 对象（不要 markdown 代码块），字段如下：
            - contractVersion: 固定为 1
            - op: CREATE | CANCEL | NOOP
            - title: 提醒标题（CREATE 时必填，简短）
            - actionText: 邮件正文动作描述（CREATE 时可选）
            - scheduleType: DAILY | WEEKLY | MONTHLY | ONCE（CREATE 时）
            - cronExpression: Spring 6 域 cron，时区 Asia/Shanghai（CREATE 时必填）
            - endsAt: ISO-8601 结束时间或 null（可选）
            - cancelReminderId: 取消时匹配到的提醒 id（CANCEL 时，单条）
            - cancelReminderIds: 批量取消时的 id 数组（可选）
            - cancelMatch: 取消匹配说明（CANCEL 时）
            - userMessage: 给用户的简短确认话术（成功时）
            - error: 无法解析时的中文原因；成功时必须为 null 或空字符串

            规则：CREATE 用于「提醒我…」；CANCEL 用于「取消提醒」并明确序号或标题。
            仅说「取消提醒」且无法唯一匹配时：op=NOOP，userMessage 列出「1、标题；2、标题」式编号清单。
            用户回复序号时按 activeReminders 列表顺序（1 表示第一条）解析，不要误把列表序号当成数据库 id。
            """;

    private final ModelInvokePort modelInvokePort;
    private final SysLlmModelRepository llmModelRepository;
    private final ObjectMapper objectMapper;

    public ReminderParseResponse parse(ReminderParseRequest req, String modelAliasOrNull) {
        if (req == null) {
            return error("请求为空");
        }
        Long tenantId = req.tenantId();
        if (tenantId == null || tenantId <= 0) {
            return error("缺少租户上下文");
        }
        String utterance = req.utterance() == null ? "" : req.utterance().trim();
        if (utterance.isEmpty()) {
            return error("请说明要提醒的内容与时间");
        }
        Optional<SysLlmModel> model = resolveLanguageModel(tenantId, modelAliasOrNull);
        if (model.isEmpty()) {
            return error("租户未配置可用的语言模型，无法智能解析");
        }
        try {
            String userJson = objectMapper.writeValueAsString(req);
            String raw = invokeJson(tenantId, model.get(), userJson);
            ReminderParseResponse parsed = parseResponseJson(raw);
            if (parsed.contractVersion() == null
                    || parsed.contractVersion() != ReminderParseContracts.CONTRACT_VERSION) {
                return error("模型返回的契约版本无效");
            }
            if (parsed.error() != null && !parsed.error().isBlank()) {
                return parsed;
            }
            String op = parsed.op() == null ? "NOOP" : parsed.op().trim().toUpperCase(Locale.ROOT);
            if ("CREATE".equals(op) && (parsed.cronExpression() == null || parsed.cronExpression().isBlank())) {
                return error("未能解析提醒时间，请补充例如「每天8点」");
            }
            if ("CANCEL".equals(op)) {
                boolean missingId =
                        (parsed.cancelReminderId() == null)
                                && (parsed.cancelReminderIds() == null
                                        || parsed.cancelReminderIds().isEmpty());
                if (missingId
                        || (parsed.error() != null && !parsed.error().isBlank())) {
                    return ReminderCancelSelectionSupport.resolveCancel(
                            utterance, req.activeReminders());
                }
            }
            return parsed;
        } catch (Exception e) {
            log.warn("[意图·提醒] LLM 解析失败 tenantId={}", tenantId, e);
            return error("智能解析暂不可用，请稍后重试或改用规则解析");
        }
    }

    private Optional<SysLlmModel> resolveLanguageModel(long tenantId, String modelAliasOrNull) {
        if (modelAliasOrNull != null && !modelAliasOrNull.isBlank()) {
            Optional<SysLlmModel> byAlias =
                    llmModelRepository.findByTenantAndAlias(tenantId, modelAliasOrNull.trim());
            if (byAlias.isPresent() && byAlias.get().getModelKind() == LlmModelKind.LANGUAGE) {
                return byAlias;
            }
        }
        return llmModelRepository.pickDefaultLanguageModel(tenantId);
    }

    private String invokeJson(long tenantId, SysLlmModel model, String userContent) throws Exception {
        LlmModelKindPolicy.assertLanguageModelForChatStream(model);
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        sys.setContent(SYSTEM_PROMPT);
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(userContent);
        var req = new ModelChatRequest();
        req.setTenantId(tenantId);
        req.setModelAlias(model.getAlias());
        req.setThinkingEnabled(false);
        req.setUsageScene(LlmUsageScene.INTENT_REMINDER_PARSE.getCode());
        req.setMessages(List.of(sys, user));
        StringBuilder acc = new StringBuilder();
        modelInvokePort.streamCompletion(req, acc::append);
        return acc.toString().trim();
    }

    private ReminderParseResponse parseResponseJson(String raw) throws Exception {
        String json = extractJsonObject(raw);
        return objectMapper.readValue(json, ReminderParseResponse.class);
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
        return t;
    }

    private static ReminderParseResponse error(String msg) {
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                msg);
    }
}
