package com.aaron.cloud.chat.support;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.document.AttachmentOcrTextQuality;
import com.aaron.cloud.common.document.ExtractedDocumentTexts;
import com.aaron.cloud.common.document.UploadFileNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话附件轮次的 RAG / 联网检索策略：检索词 = 用户原问句 + 附件 OCR/文件名中的<strong>可用</strong>实体摘要；
 * 过滤 Markdown 表格、尺寸数字等 OCR 噪声，避免「008 009E 002」类碎片进入问句改写。
 */
public final class ChatAttachmentRetrievalSupport {

    /** 与 {@link com.aaron.cloud.chat.ChatApplicationService} 拼进 user 消息的附件块起始标记。 */
    public static final String USER_ATTACHMENT_BLOCK_MARKER = "\n\n【以下为用户上传文档摘要，请结合回答】";

    private static final String REGENERATE_USER_QUESTION_MARKER = "我的问题是：";

    private static final int MAX_ATTACHMENT_HINT_CHARS = 900;
    private static final int MAX_LINES_SCAN = 80;
    private static final int MAX_RETRIEVAL_INPUT_CHARS = 1200;

    private static final Pattern ATTACHMENT_FILE_SECTION =
            Pattern.compile("---\\s*文件：[^-]+---\\s*");

    private static final Pattern MEANINGFUL_CJK = Pattern.compile("[\\u4e00-\\u9fff]{2,}");

    private static final Pattern ATTACHMENT_DEICTIC =
            Pattern.compile("(这|该|此|上面|图中|图片|照片|截图|文档|文件|附件|上传|户型|咋样|怎样|如何)");

    private static final Pattern EXTERNAL_LOOKUP_INTENT =
            Pattern.compile(
                    "(联网|上网|搜索|检索|查一下|帮我查|最新|新闻|政策|价格|房价|报价|多少钱|哪里|在哪|地址|官网|网上)");

    private ChatAttachmentRetrievalSupport() {}

    /** 有附件时不检索租户知识库（上传内容不在 KB 内）。 */
    public static boolean shouldSkipRag(List<ChatAttachment> attachments) {
        return attachments != null && !attachments.isEmpty();
    }

    /** 附件轮次不做 RAG 问句 LLM 改写（RAG 本身已跳过，且 OCR 噪声易误导改写）。 */
    public static boolean shouldSkipRagQueryRewrite(List<ChatAttachment> attachments) {
        return attachments != null && !attachments.isEmpty();
    }

    /**
     * 无可用附件实体摘要且用户在指代上传内容时，跳过联网外呼（答案应来自附件与模型）。
     */
    public static boolean shouldDeferWebSearch(List<ChatAttachment> attachments, String userText) {
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        if (hasUsableAttachmentHint(attachments)) {
            return false;
        }
        String q = userText == null ? "" : userText.trim();
        if (q.isEmpty()) {
            return true;
        }
        if (EXTERNAL_LOOKUP_INTENT.matcher(q).find()) {
            return false;
        }
        return q.length() <= 16 || ATTACHMENT_DEICTIC.matcher(q).find();
    }

    /** 附件 OCR 无可用实体时，联网固定源走规则改写，避免 LLM 编造「相关数值项情况」等词。 */
    public static boolean useConservativeWebKeywordRewrite(List<ChatAttachment> attachments) {
        return attachments != null
                && !attachments.isEmpty()
                && !hasUsableAttachmentHint(attachments);
    }

    /** 压缩后的附件摘要是否含可用于检索的实体（中文主题、型号、有意义文件名等）。 */
    public static boolean hasUsableAttachmentHint(List<ChatAttachment> attachments) {
        String hint = compactAttachmentContextForRetrieval(attachments);
        return isUsableHint(hint);
    }

    /**
     * 供 RAG / 联网问句改写与外呼使用的检索输入：无附件或 OCR 仅为表格噪声时仅返回用户问句。
     */
    public static String retrievalKeywordSource(
            ChatSendPayload payload,
            String augmentedUserText,
            String regenerateInstructionPrefix,
            List<ChatAttachment> attachments) {
        String user =
                userTurnPlaintextForRetrieval(payload, augmentedUserText, regenerateInstructionPrefix);
        if (attachments == null || attachments.isEmpty()) {
            return user;
        }
        String attachmentHint = compactAttachmentContextForRetrieval(attachments);
        if (!isUsableHint(attachmentHint)) {
            return clampRetrievalInput(user);
        }
        return formatRetrievalInputWithAttachments(user, attachmentHint);
    }

    /**
     * 从本轮 user 正文提取纯用户问句（与附件 OCR 块隔离）。
     */
    public static String userTurnPlaintextForRetrieval(
            ChatSendPayload payload, String augmentedUserText, String regenerateInstructionPrefix) {
        String aug = augmentedUserText == null ? "" : augmentedUserText.trim();
        if (regenerateInstructionPrefix != null
                && !regenerateInstructionPrefix.isBlank()
                && aug.startsWith(regenerateInstructionPrefix)) {
            int mi = aug.indexOf(REGENERATE_USER_QUESTION_MARKER);
            if (mi >= 0) {
                int bodyStart = mi + REGENERATE_USER_QUESTION_MARKER.length();
                int att = aug.indexOf(USER_ATTACHMENT_BLOCK_MARKER);
                String slice =
                        att >= bodyStart ? aug.substring(bodyStart, att).trim() : aug.substring(bodyStart).trim();
                if (!slice.isEmpty()) {
                    return slice;
                }
            }
        }
        String userOnly = payload.getContent() == null ? "" : payload.getContent().trim();
        if (!userOnly.isEmpty()) {
            return userOnly;
        }
        if (!aug.isEmpty()) {
            int att = aug.indexOf(USER_ATTACHMENT_BLOCK_MARKER);
            if (att > 0) {
                return aug.substring(0, att).trim();
            }
        }
        return "";
    }

    /** 是否全部附件均未抽出有效文本（仅占位符）。 */
    public static boolean allAttachmentsPlaceholderOnly(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        for (ChatAttachment a : attachments) {
            if (a == null) {
                continue;
            }
            if (!ExtractedDocumentTexts.isPlaceholder(a.getExtractedText())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 规则回退改写：含 {@link #formatRetrievalInputWithAttachments} 结构时合并附件实体与用户意图。
     */
    public static String heuristicRewriteForRetrievalInput(String raw, UnaryOperator<String> baseRewrite) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        UnaryOperator<String> rewrite = baseRewrite != null ? baseRewrite : s -> s == null ? "" : s.trim();
        String trimmed = raw.trim();
        if (!trimmed.contains("上传文件摘要")) {
            return rewrite.apply(trimmed);
        }
        String user = sliceBetween(trimmed, "用户问题：", "\n上传文件摘要");
        String hint = sliceAfterMarker(trimmed, "上传文件摘要");
        hint = hint.replaceFirst("^（[^）]+）[:：]?", "").trim();
        var merged = new StringBuilder();
        if (isUsableHint(hint)) {
            merged.append(rewrite.apply(hint));
        }
        if (!user.isBlank()) {
            if (!merged.isEmpty()) {
                merged.append(' ');
            }
            merged.append(rewrite.apply(user));
        }
        return merged.toString().replaceAll("\\s+", " ").trim();
    }

    static String formatRetrievalInputWithAttachments(String userQuestion, String attachmentHint) {
        String user = userQuestion == null ? "" : userQuestion.trim();
        String hint = attachmentHint == null ? "" : attachmentHint.trim();
        if (!isUsableHint(hint)) {
            return clampRetrievalInput(user);
        }
        if (user.isBlank()) {
            return clampRetrievalInput(hint);
        }
        String formatted =
                "用户问题："
                        + user
                        + "\n上传文件摘要（改写检索词须保留其中的楼盘名/编号/地名等实体，勿编造附件未出现的词）："
                        + hint;
        return clampRetrievalInput(formatted);
    }

    static String compactAttachmentContextForRetrieval(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        var parts = new ArrayList<String>();
        int budget = MAX_ATTACHMENT_HINT_CHARS;
        for (ChatAttachment a : attachments) {
            if (a == null || budget <= 0) {
                continue;
            }
            String chunk = compactOneAttachment(a, budget);
            if (!chunk.isBlank()) {
                parts.add(chunk);
                budget -= chunk.length();
            }
        }
        return String.join("；", parts).trim();
    }

    static boolean isUsableHint(String hint) {
        return AttachmentOcrTextQuality.isUsableEntityHint(hint);
    }

    static boolean isTableNoise(String text) {
        return AttachmentOcrTextQuality.isTableNoise(text);
    }

    static boolean isNoiseLine(String line) {
        return AttachmentOcrTextQuality.isNoiseLine(line);
    }

    private static String compactOneAttachment(ChatAttachment a, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        String text = a.getExtractedText();
        if (text != null && !text.isBlank() && !ExtractedDocumentTexts.isPlaceholder(text)) {
            String signals = extractRetrievalSignals(text, maxChars);
            if (isUsableHint(signals)) {
                return signals;
            }
        }
        String fileName = a.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String stem = fileNameStem(fileName);
        return isUsableHint(stem) ? stem : "";
    }

    /** 从 OCR 正文提取可用于检索的实体行/短语，丢弃 Markdown 表格与尺寸数字行。 */
    static String extractRetrievalSignals(String text, int maxChars) {
        String cleaned = ATTACHMENT_FILE_SECTION.matcher(text).replaceAll(" ").trim();
        var signals = new LinkedHashSet<String>();
        int scanned = 0;
        for (String line : cleaned.split("\\R", -1)) {
            if (scanned++ >= MAX_LINES_SCAN) {
                break;
            }
            String t = line.trim();
            if (t.isEmpty() || isNoiseLine(t)) {
                continue;
            }
            String normalized = t.replaceAll("\\s+", " ");
            if (normalized.length() > 120) {
                normalized = normalized.substring(0, 120).trim();
            }
            signals.add(normalized);
            if (signals.size() >= 8) {
                break;
            }
        }
        collectInlineCjkPhrases(cleaned, signals);
        if (signals.isEmpty()) {
            return "";
        }
        String joined = String.join(" ", signals).replaceAll("\\s+", " ").trim();
        if (joined.length() <= maxChars) {
            return joined;
        }
        return joined.substring(0, maxChars).trim() + "…";
    }

    private static void collectInlineCjkPhrases(String text, LinkedHashSet<String> out) {
        Matcher m = MEANINGFUL_CJK.matcher(text);
        while (m.find() && out.size() < 12) {
            String phrase = m.group().trim();
            if (phrase.length() >= 2 && !isCommonRoomLabelNoise(phrase)) {
                out.add(phrase);
            }
        }
    }

    private static boolean isCommonRoomLabelNoise(String phrase) {
        return phrase.length() <= 3 && "示意图".equals(phrase);
    }

    private static String fileNameStem(String fileName) {
        String normalized = UploadFileNames.normalize(fileName, "application/octet-stream");
        int dot = normalized.lastIndexOf('.');
        String stem = dot > 0 ? normalized.substring(0, dot) : normalized;
        return stem.replace('_', ' ').replace('-', ' ').trim();
    }

    private static String clampRetrievalInput(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= MAX_RETRIEVAL_INPUT_CHARS) {
            return t;
        }
        return t.substring(0, MAX_RETRIEVAL_INPUT_CHARS).trim() + "…";
    }

    private static String sliceBetween(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        start += startMarker.length();
        int end = text.indexOf(endMarker, start);
        if (end < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, end).trim();
    }

    private static String sliceAfterMarker(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) {
            return "";
        }
        return text.substring(idx + marker.length()).trim();
    }
}
