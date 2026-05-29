package com.aaron.cloud.chat;

import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 向主对话与联网 grounding 注入「当前真实日期」，避免模型将检索结果中的报道日期误判为幻觉。 */
public final class ChatTemporalContextPrompt {

    private static final DateTimeFormatter ZH_DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private ChatTemporalContextPrompt() {}

    /** 追加至主对话 system 段（在 persona / RAG / 语种指令附近）。 */
    public static void appendToSystemPrompt(StringBuilder sys, String responseLocale) {
        if (sys == null) {
            return;
        }
        if (!sys.isEmpty()) {
            sys.append("\n\n");
        }
        sys.append(
                ChatResponseLocalePrompt.isEnglish(responseLocale)
                        ? buildEnglishSystemBlock(today())
                        : buildChineseSystemBlock(today()));
    }

    /** 联网检索注入段首部：强调检索结果为真实外部数据，日期不超过「今天」时应信其时效。 */
    public static String webGroundingPreamble(String responseLocale) {
        LocalDate today = today();
        return ChatResponseLocalePrompt.isEnglish(responseLocale)
                ? buildEnglishWebGroundingPreamble(today)
                : buildChineseWebGroundingPreamble(today);
    }

    static LocalDate today() {
        return BeijingTime.today();
    }

    private static String buildChineseSystemBlock(LocalDate today) {
        return """
                【时间上下文】当前真实日期（中国时区 Asia/Shanghai）：%s（%s）。\
                请据此理解用户口中的「今年」「现在」「最近」「昨天」等时间词；\
                若下文出现联网检索结果，其中报道日期不超过上述「今天」的，应视为真实外部信息而非模型编造。"""
                .formatted(today.format(ZH_DATE), today.format(ISO_DATE));
    }

    private static String buildEnglishSystemBlock(LocalDate today) {
        return """
                [Temporal context] The actual current date (Asia/Shanghai) is %s (%s). \
                Use this when interpreting "this year", "now", "recently", "yesterday", etc. \
                If web search results appear below, treat their publication dates (when not after today) as real external facts, not model hallucination."""
                .formatted(today.format(ISO_DATE), today.format(ZH_DATE));
    }

    private static String buildChineseWebGroundingPreamble(LocalDate today) {
        return """
                【时间上下文】当前真实日期：%s（%s）。\
                以下「网络检索摘要/引用」来自外部联网抓取；其中出现的发布日期、报道年份若不超过上述「今天」，均为真实检索结果，不是模型幻觉或「模拟未来背景」。\
                请结合当前日期判断时效性并据此作答；勿将整段检索内容当作虚构设定。"""
                .formatted(today.format(ZH_DATE), today.format(ISO_DATE));
    }

    private static String buildEnglishWebGroundingPreamble(LocalDate today) {
        return """
                [Temporal context] Actual current date: %s (%s). \
                The web search summary and citations below are from live retrieval. \
                Publication dates not after today are real external facts—not hallucination or a simulated future. \
                Use them with the current date for timeliness; do not treat the block as fiction."""
                .formatted(today.format(ISO_DATE), today.format(ZH_DATE));
    }
}
