package com.aaron.cloud.chat.reminder;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.intent.IntentKeywordMatchHit;
import com.aaron.cloud.chat.intent.ReminderCancelSelectionGate;
import com.aaron.cloud.chat.intent.mcp.IntentMcpSingleInvoke;
import com.aaron.cloud.chat.intent.mcp.IntentMcpToolBinding;
import com.aaron.cloud.chat.intent.reminder.ReminderHandlerParams;
import com.aaron.cloud.common.api.enums.chat.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.chat.ChatUserReminderStatus;
import com.aaron.cloud.common.api.enums.chat.ReminderScheduleType;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.aaron.cloud.common.chat.ChatUserReminderRepository;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatUserReminder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.time.BeijingTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUserReminderOrchestrator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 访客或未登录：无 userId，无法使用全局邮件提醒。 */
    static final String MSG_NOT_LOGGED_IN =
            "抱歉，邮件提醒需要先登录账号才能使用（登录后还需在账号设置中绑定邮箱）。请先登录后再试。";

    /** 已登录但未绑定邮箱：无法创建定时邮件提醒。 */
    static final String MSG_NO_EMAIL =
            "抱歉，创建邮件提醒需要先在账号设置中绑定邮箱。绑定邮箱后再对我说「提醒我…」即可。";

    private final ChatUserReminderRepository reminderRepository;
    private final TenantScheduledTaskRepository scheduledTaskRepository;
    private final McpInvokePort mcpInvokePort;
    private final ObjectMapper objectMapper;
    private final SecUserAccountRepository userAccountRepository;
    private final MessageSceneReadinessQuery messageSceneReadinessQuery;

    public TurnResult handleTurn(
            long conversationId,
            TenantSnapshot snap,
            ChatSendPayload payload,
            ChatIntentDefinition def,
            IntentKeywordMatchHit matchHit) {
        Long userId = snap.getUserId();
        String utterance = payload.getContent() == null ? "" : payload.getContent().strip();
        log.info(
                "[意图·提醒] ① 编排开始 tenantId={} conversationId={} userId={} intentId={} utterance={}",
                snap.getTenantId(),
                conversationId,
                userId,
                def.getId(),
                abbreviate(utterance, 80));
        if (userId == null) {
            log.info("[意图·提醒] ② 前置失败 reason=未登录");
            return TurnResult.fail(MSG_NOT_LOGGED_IN);
        }
        ReminderHandlerParams params = ReminderHandlerParams.parse(def.getExtraConfigJson(), objectMapper);
        List<ChatUserReminder> active = reminderRepository.listActiveByUser(snap.getTenantId(), userId);
        int count = active.size();
        log.info(
                "[意图·提醒] 配置 parseToolKind={} mcpTool={} activeCount={} maxActive={}",
                params.parseToolKind(),
                params.mcpQualifiedToolName(),
                count,
                params.maxActiveReminders());

        String mcpAction = resolveMcpAction(matchHit);
        if ("CREATE".equals(mcpAction)) {
            var user = userAccountRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.info("[意图·提醒] ② 前置失败 tenantId={} userId={} reason=未绑定邮箱", snap.getTenantId(), userId);
                return TurnResult.fail(MSG_NO_EMAIL);
            }
            if (!messageSceneReadinessQuery.isSceneConfigured(
                    snap.getTenantId(), MessageSceneCode.CHAT_USER_REMINDER)) {
                log.warn(
                        "[意图·提醒] ② 前置失败 tenantId={} reason=CHAT_USER_REMINDER 邮件场景未配置",
                        snap.getTenantId());
                return TurnResult.fail("租户尚未配置「对话用户提醒」邮件场景，请联系管理员");
            }
            if (count >= params.maxActiveReminders()) {
                log.info(
                        "[意图·提醒] ② 前置失败 tenantId={} userId={} reason=有效提醒达上限 count={}",
                        snap.getTenantId(),
                        userId,
                        count);
                return TurnResult.fail("有效提醒已达上限（" + params.maxActiveReminders() + " 条），请先取消部分提醒");
            }
        }

        ReminderParseRequest mcpReq =
                new ReminderParseRequest(
                        ReminderParseContracts.CONTRACT_VERSION,
                        mcpAction,
                        payload.getContent(),
                        userId,
                        snap.getTenantId(),
                        "zh-CN",
                        count,
                        active.stream()
                                .map(
                                        r ->
                                                new ActiveReminderRef(
                                                        r.getId(),
                                                        r.getTitle(),
                                                        r.getScheduleType() == null
                                                                ? null
                                                                : r.getScheduleType().name()))
                                .toList());

        ObjectNode args = objectMapper.valueToTree(mcpReq);
        if (params.parseModelAlias() != null && !params.parseModelAlias().isBlank()) {
            args.put("parseModelAlias", params.parseModelAlias().trim());
        }
        IntentMcpToolBinding binding = new IntentMcpToolBinding(params.mcpQualifiedToolName());
        try {
            log.info(
                    "[意图·提醒] ③ 调用话术解析 mcpAction={} tool={}",
                    mcpAction,
                    binding.qualifiedName());
            var mcp =
                    IntentMcpSingleInvoke.invokeOnce(
                            mcpInvokePort, snap.getTenantId(), binding, args, objectMapper);
            if (mcp.failed()) {
                String userMsg = mcp.userFacingMessage();
                log.warn(
                        "[意图·提醒] ④ 解析失败 tenantId={} userId={} stage=mcpTransport userMsg={} rawPreview={}",
                        snap.getTenantId(),
                        userId,
                        userMsg,
                        abbreviate(mcp.rawText(), 200));
                return TurnResult.fail(userMsg);
            }
            ReminderParseResponse parsed = mcp.bodyAs(objectMapper, ReminderParseResponse.class);
            if (parsed.error() != null && !parsed.error().isBlank()) {
                log.info(
                        "[意图·提醒] ④ 解析业务失败 tenantId={} userId={} reason={}",
                        snap.getTenantId(),
                        userId,
                        parsed.error());
                return TurnResult.fail(parsed.error());
            }
            String op = parsed.op() == null ? "NOOP" : parsed.op().trim().toUpperCase();
            log.info(
                    "[意图·提醒] ④ 解析成功 tenantId={} userId={} op={} title={} cron={}",
                    snap.getTenantId(),
                    userId,
                    op,
                    parsed.title(),
                    parsed.cronExpression());
            return switch (op) {
                case "CREATE" -> applyCreate(conversationId, snap, def, matchHit, params, mcpReq, parsed, active);
                case "CANCEL" -> applyCancel(snap, def, matchHit, parsed, active);
                default -> applyNoop(snap, def, matchHit, parsed, active);
            };
        } catch (Exception e) {
            log.warn(
                    "[意图·提醒] ④ 解析异常 tenantId={} userId={} tool={}",
                    snap.getTenantId(),
                    userId,
                    binding.qualifiedName(),
                    e);
            return TurnResult.fail("提醒解析服务暂不可用，请稍后重试");
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    @Transactional
    protected TurnResult applyCreate(
            long conversationId,
            TenantSnapshot snap,
            ChatIntentDefinition def,
            IntentKeywordMatchHit matchHit,
            ReminderHandlerParams params,
            ReminderParseRequest mcpReq,
            ReminderParseResponse parsed,
            List<ChatUserReminder> active) {
        String cron = parsed.cronExpression();
        if (cron == null || cron.isBlank()) {
            return TurnResult.fail("未能解析提醒时间，请补充例如「每天8点」");
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (Exception e) {
            return TurnResult.fail("提醒时间表达式无效，请换一种说法");
        }
        ReminderScheduleType st = ReminderScheduleType.fromMcpName(parsed.scheduleType());
        if (st == null) {
            return TurnResult.fail("未能识别提醒周期类型");
        }
        LocalDateTime endsAt = resolveEndsAt(parsed.endsAt(), params.defaultMaxDays());
        if (endsAt.isBefore(BeijingTime.nowLocal())) {
            return TurnResult.fail("提醒截止时间不能早于当前时间");
        }

        var task = new TenantScheduledTask();
        task.setTenantId(snap.getTenantId());
        task.setExecutorCode(TenantScheduledExecutorCode.CHAT_USER_REMINDER);
        task.setTaskType(TenantScheduledExecutorCode.CHAT_USER_REMINDER.getCode());
        task.setName("提醒：" + (parsed.title() == null ? "未命名" : parsed.title()));
        task.setCronExpression(cron.trim());
        task.setEnabled(1);
        task.setNextExecAt(computeNextExecAt(task.getCronExpression(), BeijingTime.nowLocal()));
        scheduledTaskRepository.insert(task);

        var row = new ChatUserReminder();
        row.setTenantId(snap.getTenantId());
        row.setUserId(snap.getUserId());
        row.setRegistrationId(task.getId());
        row.setConversationId(conversationId);
        row.setIntentDefinitionId(def.getId());
        row.setTitle(parsed.title());
        row.setActionText(parsed.actionText());
        row.setScheduleType(st);
        row.setCronExpression(cron.trim());
        row.setEndsAt(endsAt);
        row.setStatus(ChatUserReminderStatus.ACTIVE);
        row.setSendCount(0);
        try {
            row.setMcpRequestJson(objectMapper.writeValueAsString(mcpReq));
            row.setMcpResponseJson(objectMapper.writeValueAsString(parsed));
        } catch (Exception ignored) {
            // meta optional
        }
        reminderRepository.insert(row);

        task.setName("提醒：" + parsed.title() + " (#" + row.getId() + ")");
        scheduledTaskRepository.updateById(task);

        List<ChatUserReminder> after = reminderRepository.listActiveByUser(snap.getTenantId(), snap.getUserId());
        String msg =
                parsed.userMessage() != null && !parsed.userMessage().isBlank()
                        ? parsed.userMessage()
                        : "已创建邮件提醒。";
        return TurnResult.ok(msg, def, matchHit, after, "CREATE");
    }

    @Transactional
    protected TurnResult applyCancel(
            TenantSnapshot snap,
            ChatIntentDefinition def,
            IntentKeywordMatchHit matchHit,
            ReminderParseResponse parsed,
            List<ChatUserReminder> activeBefore) {
        List<Long> ids = resolveCancelReminderIds(parsed);
        if (ids.isEmpty()) {
            return TurnResult.fail("未能确定要取消的提醒");
        }
        int cancelled = 0;
        for (Long rid : ids) {
            var opt = reminderRepository.findById(snap.getTenantId(), rid);
            if (opt.isEmpty() || opt.get().getStatus() != ChatUserReminderStatus.ACTIVE) {
                continue;
            }
            ChatUserReminder row = opt.get();
            if (!row.getUserId().equals(snap.getUserId())) {
                return TurnResult.fail("无权取消该提醒");
            }
            row.setStatus(ChatUserReminderStatus.CANCELLED);
            reminderRepository.updateById(row);
            scheduledTaskRepository
                    .findById(snap.getTenantId(), row.getRegistrationId())
                    .ifPresent(
                            t -> {
                                t.setEnabled(0);
                                scheduledTaskRepository.updateById(t);
                            });
            cancelled++;
        }
        if (cancelled == 0) {
            return TurnResult.fail("该提醒不存在或已取消");
        }
        List<ChatUserReminder> after = reminderRepository.listActiveByUser(snap.getTenantId(), snap.getUserId());
        String msg =
                parsed.userMessage() != null && !parsed.userMessage().isBlank()
                        ? parsed.userMessage().replaceFirst("^将取消", "已取消")
                        : cancelled > 1
                                ? "已取消 " + cancelled + " 条提醒。"
                                : "已取消该提醒。";
        return TurnResult.ok(msg, def, matchHit, after, "CANCEL");
    }

    private static List<Long> resolveCancelReminderIds(ReminderParseResponse parsed) {
        if (parsed.cancelReminderIds() != null && !parsed.cancelReminderIds().isEmpty()) {
            return parsed.cancelReminderIds();
        }
        if (parsed.cancelReminderId() != null) {
            return List.of(parsed.cancelReminderId());
        }
        return List.of();
    }

    protected TurnResult applyNoop(
            TenantSnapshot snap,
            ChatIntentDefinition def,
            IntentKeywordMatchHit matchHit,
            ReminderParseResponse parsed,
            List<ChatUserReminder> active) {
        String msg =
                parsed.userMessage() != null && !parsed.userMessage().isBlank()
                        ? parsed.userMessage()
                        : "当前共有 " + active.size() + " 条有效提醒。";
        return TurnResult.ok(msg, def, matchHit, active, "NOOP");
    }

    private static String resolveMcpAction(IntentKeywordMatchHit hit) {
        if (hit.keywordKind() == ChatIntentKeywordKind.CANCEL) {
            return "CANCEL";
        }
        if (hit.keywordKind() == ChatIntentKeywordKind.TRIGGER) {
            return "CREATE";
        }
        return "AUTO";
    }

    private static LocalDateTime computeNextExecAt(String cron, LocalDateTime after) {
        CronExpression expression = CronExpression.parse(cron.trim());
        ZonedDateTime base = (after != null ? after : LocalDateTime.now()).atZone(ZONE);
        ZonedDateTime next = expression.next(base);
        return next != null ? next.toLocalDateTime() : null;
    }

    private static LocalDateTime resolveEndsAt(String endsAtRaw, int defaultMaxDays) {
        if (endsAtRaw != null && !endsAtRaw.isBlank()) {
            try {
                return LocalDateTime.parse(endsAtRaw.strip());
            } catch (DateTimeParseException e) {
                return BeijingTime.nowLocal().plusDays(defaultMaxDays);
            }
        }
        return BeijingTime.nowLocal().plusDays(defaultMaxDays);
    }

    public record TurnResult(
            boolean success,
            String assistantText,
            String assistantMetaJson,
            String userFacingError) {

        public static TurnResult fail(String err) {
            return new TurnResult(false, null, null, err);
        }

        public static TurnResult ok(
                String text,
                ChatIntentDefinition def,
                IntentKeywordMatchHit hit,
                List<ChatUserReminder> active,
                String op) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode n = mapper.createObjectNode();
                n.put("intentHandled", true);
                n.put("intentId", def.getId());
                n.put("intentCode", def.getCode());
                n.put("intentDisplayName", def.getDisplayName());
                n.put("intentMatchSource", hit.matchSource().name());
                if (hit.keywordId() != null) {
                    n.put("intentHitKeywordId", hit.keywordId());
                }
                n.put("intentHitPhrase", hit.matchedPhrase() == null ? "" : hit.matchedPhrase());
                if (hit.keywordKind() != null) {
                    n.put("intentHitKeywordKind", hit.keywordKind().name());
                }
                n.put("activeReminderCount", active.size());
                n.put("reminderOp", op);
                n.put(
                        ReminderCancelSelectionGate.META_CANCEL_SELECTION_PENDING,
                        "NOOP".equals(op));
                if (hit.intentFlowRound() != null) {
                    n.put("intentFlowRound", hit.intentFlowRound());
                }
                ArrayNode preview = n.putArray("reminderPreview");
                int limit = Math.min(3, active.size());
                for (int i = 0; i < limit; i++) {
                    ChatUserReminder r = active.get(i);
                    ObjectNode p = preview.addObject();
                    p.put("id", r.getId());
                    p.put("title", r.getTitle());
                    if (r.getScheduleType() != null) {
                        p.put("scheduleType", r.getScheduleType().name());
                    }
                }
                return new TurnResult(true, text, mapper.writeValueAsString(n), null);
            } catch (Exception e) {
                return new TurnResult(true, text, null, null);
            }
        }
    }
}
