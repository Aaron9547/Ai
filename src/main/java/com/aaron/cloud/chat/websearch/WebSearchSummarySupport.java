package com.aaron.cloud.chat.websearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 由检索引用列表拼装供主模型阅读的摘要文本。 */
public final class WebSearchSummarySupport {

    private static final int DEFAULT_MAX_BULLETS = 8;
    private static final String PIECE_SEP = "\n\n---\n\n";
    private static final Pattern DATE_IN_SUMMARY =
            Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");

    private WebSearchSummarySupport() {}

    public static String bulletsFromReferences(List<WebSearchReference> refs) {
        return bulletsFromReferences(refs, DEFAULT_MAX_BULLETS);
    }

    public static String bulletsFromReferences(List<WebSearchReference> refs, int maxBullets) {
        if (refs == null || refs.isEmpty()) {
            return "";
        }
        int cap = Math.max(1, maxBullets);
        List<String> lines = new ArrayList<>();
        int n = 0;
        for (WebSearchReference r : refs) {
            if (r == null || n >= cap) {
                break;
            }
            String title = r.title() == null ? "" : r.title().trim();
            String snip = r.snippet() == null ? "" : r.snippet().trim();
            if (title.isEmpty() && snip.isEmpty()) {
                continue;
            }
            if (!snip.isEmpty()) {
                lines.add("- " + (title.isEmpty() ? snip : title + "：" + snip));
            } else {
                lines.add("- " + title);
            }
            n++;
        }
        return String.join("\n", lines);
    }

    public static String prefixSummary(String sourceLabel, String body) {
        String label = sourceLabel == null ? "" : sourceLabel.trim();
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (label.isEmpty()) {
            return text;
        }
        return "[" + label + "]\n" + text;
    }

    /** 合并两段摘要（按 {@value #PIECE_SEP} 分段后去重再拼接）。 */
    public static String joinSummaryText(String a, String b) {
        List<String> pieces = new ArrayList<>();
        pieces.addAll(splitSummaryPieces(a));
        pieces.addAll(splitSummaryPieces(b));
        return joinSummaryPieces(dedupeSummaryPieces(pieces));
    }

    /** 多段摘要列表合并为单字符串（去重）。 */
    public static String joinSummaryPieces(List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return "";
        }
        return String.join(PIECE_SEP, dedupeSummaryPieces(pieces));
    }

    public static List<String> splitSummaryPieces(String summary) {
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : summary.split(Pattern.quote(PIECE_SEP))) {
            String t = part == null ? "" : part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return List.copyOf(out);
    }

    /** 去掉多轮/多源产生的重复摘要块（同日期或高相似保留较长一段）。 */
    public static List<String> dedupeSummaryPieces(List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String piece : pieces) {
            if (piece == null || piece.isBlank()) {
                continue;
            }
            String candidate = piece.trim();
            int replaceAt = findDuplicateIndex(kept, candidate);
            if (replaceAt >= 0) {
                if (candidate.length() > kept.get(replaceAt).length()) {
                    kept.set(replaceAt, candidate);
                }
                continue;
            }
            kept.add(candidate);
        }
        return List.copyOf(kept);
    }

    private static int findDuplicateIndex(List<String> kept, String candidate) {
        String normC = normalizeForCompare(candidate);
        String dateC = extractSummaryDateKey(candidate);
        for (int i = 0; i < kept.size(); i++) {
            String existing = kept.get(i);
            String normE = normalizeForCompare(existing);
            if (normE.equals(normC)) {
                return i;
            }
            if (normE.contains(normC) || normC.contains(normE)) {
                return i;
            }
            if (dateC != null && dateC.equals(extractSummaryDateKey(existing))) {
                return i;
            }
            if (normC.length() >= 120
                    && normE.length() >= 120
                    && tokenOverlapRatio(normE, normC) >= 0.55) {
                return i;
            }
        }
        return -1;
    }

    static String extractSummaryDateKey(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = DATE_IN_SUMMARY.matcher(text.substring(0, Math.min(280, text.length())));
        if (!m.find()) {
            return null;
        }
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        return String.format(Locale.ROOT, "%s-%02d-%02d", m.group(1), month, day);
    }

    private static String normalizeForCompare(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static double tokenOverlapRatio(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        LinkedHashSet<String> ta = new LinkedHashSet<>();
        for (int i = 0; i + 4 <= a.length(); i += 4) {
            ta.add(a.substring(i, Math.min(i + 4, a.length())));
        }
        LinkedHashSet<String> tb = new LinkedHashSet<>();
        for (int i = 0; i + 4 <= b.length(); i += 4) {
            tb.add(b.substring(i, Math.min(i + 4, b.length())));
        }
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String t : ta) {
            if (tb.contains(t)) {
                inter++;
            }
        }
        return (2.0 * inter) / (ta.size() + tb.size());
    }
}
