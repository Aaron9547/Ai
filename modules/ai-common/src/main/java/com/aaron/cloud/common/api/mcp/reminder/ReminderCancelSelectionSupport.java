package com.aaron.cloud.common.api.mcp.reminder;

import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 取消提醒：编号清单展示与列表序号解析（1、2 / 1,2 / 1和2；无分隔的 {@code 12} 视为序号 12 而非 1+2）。 */
public final class ReminderCancelSelectionSupport {

    public static final String INVALID_INDEX_USER_MESSAGE = "没有这个提醒哦！";

    private static final Pattern LIST_INDEX_LEAD =
            Pattern.compile("(?:编号|第)?\\s*(\\d+)\\s*条?");
    private static final Pattern INDEX_SEPARATORS = Pattern.compile("[、,，和与及\\s]+");

    private ReminderCancelSelectionSupport() {}

    public static ReminderParseResponse resolveCancel(String utterance, List<ActiveReminderRef> active) {
        if (active == null || active.isEmpty()) {
            return error("当前没有可取消的提醒");
        }
        String normalized = normalize(utterance);
        List<ActiveReminderRef> titleHits = matchByTitle(normalized, active);
        if (titleHits.size() == 1) {
            return cancelOne(titleHits.getFirst());
        }
        if (titleHits.size() > 1) {
            return promptChooseList(active);
        }

        String selection = stripCancelNoise(normalized);
        if (selection.isEmpty()) {
            return promptChooseList(active);
        }

        IndexParseOutcome indices = parseListIndices(selection, active.size());
        return switch (indices.kind()) {
            case NOT_SELECTION -> promptChooseList(active);
            case INVALID -> error(INVALID_INDEX_USER_MESSAGE);
            case VALID -> cancelByIndices(active, indices.indices());
        };
    }

    public static String formatChoiceList(List<ActiveReminderRef> active) {
        StringBuilder sb = new StringBuilder("请选择要取消的提醒编号：");
        for (int i = 0; i < active.size(); i++) {
            if (i > 0) {
                sb.append('；');
            }
            ActiveReminderRef r = active.get(i);
            String title =
                    r.title() == null || r.title().isBlank() ? "未命名" : r.title().strip();
            sb.append(i + 1).append('、').append(title);
        }
        return sb.toString();
    }

    public static IndexParseOutcome parseListIndices(String selectionPart, int listSize) {
        if (selectionPart == null || selectionPart.isBlank() || listSize <= 0) {
            return IndexParseOutcome.notSelection();
        }
        String t = selectionPart.strip();
        if (!t.chars().allMatch(Character::isDigit) && !looksLikeIndexSelection(t)) {
            return IndexParseOutcome.notSelection();
        }
        List<Integer> raw;
        if (INDEX_SEPARATORS.matcher(t).find()) {
            raw = new ArrayList<>();
            for (String part : INDEX_SEPARATORS.split(t)) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (!part.chars().allMatch(Character::isDigit)) {
                    return IndexParseOutcome.notSelection();
                }
                raw.add(Integer.parseInt(part));
            }
        } else if (t.chars().allMatch(Character::isDigit)) {
            raw = List.of(Integer.parseInt(t));
        } else {
            Matcher m = LIST_INDEX_LEAD.matcher(t);
            if (!m.find()) {
                return IndexParseOutcome.notSelection();
            }
            raw = List.of(Integer.parseInt(m.group(1)));
        }
        if (raw.isEmpty()) {
            return IndexParseOutcome.notSelection();
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (int idx : raw) {
            if (idx < 1 || idx > listSize) {
                return IndexParseOutcome.invalid();
            }
            unique.add(idx);
        }
        return IndexParseOutcome.valid(List.copyOf(unique));
    }

    public static String stripCancelNoise(String utterance) {
        String t = normalize(utterance);
        t = t.replaceAll("取消提醒|关闭提醒|不要提醒|停止提醒|取消通知", "");
        t = t.replaceAll("取消|关闭|停止", "");
        return t.strip();
    }

    private static boolean looksLikeIndexSelection(String t) {
        return LIST_INDEX_LEAD.matcher(t).find()
                || INDEX_SEPARATORS.matcher(t).find()
                || t.chars().allMatch(Character::isDigit);
    }

    private static List<ActiveReminderRef> matchByTitle(String utterance, List<ActiveReminderRef> active) {
        List<ActiveReminderRef> hits = new ArrayList<>();
        for (ActiveReminderRef r : active) {
            String title = r.title() == null ? "" : r.title().strip();
            if (!title.isEmpty() && utterance.contains(title)) {
                hits.add(r);
            }
        }
        return hits;
    }

    private static ReminderParseResponse cancelByIndices(
            List<ActiveReminderRef> active, List<Integer> oneBasedIndices) {
        List<ActiveReminderRef> targets = new ArrayList<>();
        for (int idx : oneBasedIndices) {
            targets.add(active.get(idx - 1));
        }
        return cancelMany(targets);
    }

    private static ReminderParseResponse cancelOne(ActiveReminderRef one) {
        return cancelMany(List.of(one));
    }

    private static ReminderParseResponse cancelMany(List<ActiveReminderRef> targets) {
        List<Long> ids =
                targets.stream()
                        .map(ActiveReminderRef::id)
                        .filter(Objects::nonNull)
                        .toList();
        if (ids.isEmpty()) {
            return error("未能确定要取消的提醒");
        }
        if (targets.size() == 1) {
            ActiveReminderRef one = targets.getFirst();
            String title = one.title() == null ? "" : one.title();
            return new ReminderParseResponse(
                    ReminderParseContracts.CONTRACT_VERSION,
                    "CANCEL",
                    title,
                    null,
                    null,
                    null,
                    null,
                    one.id(),
                    ids,
                    title,
                    "将取消提醒：「" + title + "」",
                    null);
        }
        StringBuilder titles = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                titles.append('、');
            }
            String t = targets.get(i).title();
            titles.append('「').append(t == null || t.isBlank() ? "未命名" : t).append('」');
        }
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                "CANCEL",
                null,
                null,
                null,
                null,
                null,
                ids.getFirst(),
                ids,
                null,
                "将取消 " + targets.size() + " 条提醒：" + titles,
                null);
    }

    private static ReminderParseResponse promptChooseList(List<ActiveReminderRef> active) {
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                "NOOP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                formatChoiceList(active),
                null);
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

    private static String normalize(String s) {
        return s == null ? "" : s.strip();
    }

    public enum IndexParseKind {
        NOT_SELECTION,
        VALID,
        INVALID
    }

    public record IndexParseOutcome(IndexParseKind kind, List<Integer> indices) {
        static IndexParseOutcome notSelection() {
            return new IndexParseOutcome(IndexParseKind.NOT_SELECTION, List.of());
        }

        static IndexParseOutcome invalid() {
            return new IndexParseOutcome(IndexParseKind.INVALID, List.of());
        }

        static IndexParseOutcome valid(List<Integer> indices) {
            return new IndexParseOutcome(IndexParseKind.VALID, indices);
        }
    }
}
