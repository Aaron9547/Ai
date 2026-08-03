package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 组装火山 Ark 联网 Bot 的 messages（system + 近 1～2 轮 user/assistant + 本轮 user；不含画像）。 */
public final class WebSearchArkContextBuilder {

    private static final String ARK_SYSTEM_DIRECTIVE_TEMPLATE =
            """
            你是联网检索助手。当前真实日期（中国时区）：%s（%s）。\
            结合用户最近几轮对话与「当前这一轮」的问题主动联网查找最新、相关的事实与可引用来源；勿编造。
            """;

    private WebSearchArkContextBuilder() {}

    public static List<ModelChatRequest.MessageTurn> build(String currentUserText) {
        return build(currentUserText, "", List.of());
    }

    public static List<ModelChatRequest.MessageTurn> build(
            String currentUserText, String roundSuffix, List<ModelChatRequest.MessageTurn> recentHistory) {
        var sys = new ModelChatRequest.MessageTurn();
        sys.setRole("system");
        var today = BeijingTime.today();
        String zhDate =
                today.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE));
        sys.setContent(
                ARK_SYSTEM_DIRECTIVE_TEMPLATE.formatted(zhDate, today).trim());
        List<ModelChatRequest.MessageTurn> out = new ArrayList<>();
        out.add(sys);
        appendRecentUserAssistantTurns(out, recentHistory);
        String userBody = currentUserText == null ? "" : currentUserText.trim();
        String suffix = roundSuffix == null ? "" : roundSuffix;
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(userBody + suffix);
        out.add(user);
        return List.copyOf(out);
    }

    /** 将近史 user/assistant 追加到 messages（跳过空内容与非法 role）。 */
    public static void appendRecentUserAssistantTurns(
            List<ModelChatRequest.MessageTurn> target,
            List<ModelChatRequest.MessageTurn> recentHistory) {
        if (target == null || recentHistory == null || recentHistory.isEmpty()) {
            return;
        }
        for (ModelChatRequest.MessageTurn t : recentHistory) {
            if (t == null || t.getRole() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String body = t.getContent() == null ? "" : t.getContent().trim();
            if (body.isEmpty()) {
                continue;
            }
            var copy = new ModelChatRequest.MessageTurn();
            copy.setRole(role);
            copy.setContent(body);
            target.add(copy);
        }
    }

    public static WebSearchArkInvokeRequest toInvokeRequest(
            String currentUserText, String roundSuffix, List<ModelChatRequest.MessageTurn> recentHistory) {
        List<ModelChatRequest.MessageTurn> messages = build(currentUserText, roundSuffix, recentHistory);
        String log =
                WebSearchQueryRewriteService.clipForLog(
                        currentUserText == null ? "" : currentUserText);
        return new WebSearchArkInvokeRequest(log, messages);
    }
}
