package com.aaron.cloud.chat.recommend;

import com.aaron.cloud.chat.recommend.ChatDailyRecommendJsonSupport.DailyRecommendItemRecord;
import com.aaron.cloud.chat.websearch.WebSearchReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 今日推荐条目 URL：规范化 + 与联网引用对齐（避免结构化模型编造链接）。 */
final class ChatDailyRecommendUrlSupport {

    private static final Pattern TRAILING_JUNK = Pattern.compile("[\\s.,;:!?）)】」』>]+$");

    private ChatDailyRecommendUrlSupport() {}

    static List<DailyRecommendItemRecord> attachReferenceUrls(
            List<DailyRecommendItemRecord> items, List<WebSearchReference> references) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<WebSearchReference> pool = new ArrayList<>();
        if (references != null) {
            for (WebSearchReference r : references) {
                if (r != null && isValidHttpUrl(normalizeHttpUrl(r.url()))) {
                    pool.add(r);
                }
            }
        }
        Set<Integer> usedRef = new HashSet<>();
        List<DailyRecommendItemRecord> out = new ArrayList<>(items.size());
        for (DailyRecommendItemRecord item : items) {
            String url = normalizeHttpUrl(item.url());
            if (!isValidHttpUrl(url) && !pool.isEmpty()) {
                int idx = bestMatchRefIndex(item, pool, usedRef);
                if (idx >= 0) {
                    url = normalizeHttpUrl(pool.get(idx).url());
                    usedRef.add(idx);
                }
            } else if (isValidHttpUrl(url) && !pool.isEmpty()) {
                int idx = findRefIndexByUrl(url, pool);
                if (idx >= 0) {
                    usedRef.add(idx);
                } else {
                    int match = bestMatchRefIndex(item, pool, usedRef);
                    if (match >= 0) {
                        url = normalizeHttpUrl(pool.get(match).url());
                        usedRef.add(match);
                    }
                }
            }
            out.add(
                    new DailyRecommendItemRecord(
                            item.tag(),
                            item.title(),
                            item.summary(),
                            item.source(),
                            item.date(),
                            url));
        }
        return out;
    }

    static String formatReferencesForPrompt(List<WebSearchReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (WebSearchReference r : references) {
            if (r == null) {
                continue;
            }
            String url = normalizeHttpUrl(r.url());
            if (!isValidHttpUrl(url)) {
                continue;
            }
            n++;
            sb.append(n)
                    .append(". title=")
                    .append(safePromptField(r.title()))
                    .append(" | url=")
                    .append(url);
            String site = r.siteName();
            if (site != null && !site.isBlank()) {
                sb.append(" | source=").append(safePromptField(site));
            }
            sb.append('\n');
        }
        if (n == 0) {
            return "";
        }
        return sb.toString();
    }

    static String normalizeHttpUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String u = TRAILING_JUNK.matcher(raw.trim()).replaceAll("");
        if (u.isEmpty()) {
            return "";
        }
        if (u.regionMatches(true, 0, "www.", 0, 4)) {
            u = "https://" + u;
        } else if (u.startsWith("//")) {
            u = "https:" + u;
        }
        return u;
    }

    static boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String s = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(s) && !"https".equals(s)) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private static int findRefIndexByUrl(String url, List<WebSearchReference> pool) {
        String norm = url.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < pool.size(); i++) {
            String ru = normalizeHttpUrl(pool.get(i).url()).toLowerCase(Locale.ROOT);
            if (!ru.isEmpty() && ru.equals(norm)) {
                return i;
            }
        }
        return -1;
    }

    private static int bestMatchRefIndex(
            DailyRecommendItemRecord item, List<WebSearchReference> pool, Set<Integer> used) {
        String title = item.title() == null ? "" : item.title().trim().toLowerCase(Locale.ROOT);
        String source = item.source() == null ? "" : item.source().trim().toLowerCase(Locale.ROOT);
        int bestIdx = -1;
        int bestScore = 0;
        for (int i = 0; i < pool.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            WebSearchReference r = pool.get(i);
            int score = scoreRef(item, title, source, r);
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestScore >= 40 ? bestIdx : -1;
    }

    private static int scoreRef(
            DailyRecommendItemRecord item,
            String title,
            String source,
            WebSearchReference r) {
        int score = 0;
        String rt = r.title() == null ? "" : r.title().trim().toLowerCase(Locale.ROOT);
        if (!title.isEmpty() && !rt.isEmpty()) {
            if (rt.equals(title)) {
                score += 100;
            } else if (rt.contains(title) || title.contains(rt)) {
                score += 55;
            } else if (shareLongToken(title, rt)) {
                score += 45;
            }
        }
        String site = r.siteName() == null ? "" : r.siteName().trim().toLowerCase(Locale.ROOT);
        if (!source.isEmpty() && !site.isEmpty()) {
            if (site.equals(source) || site.contains(source) || source.contains(site)) {
                score += 35;
            }
        }
        String snippet = r.snippet() == null ? "" : r.snippet();
        if (!title.isEmpty() && snippet.contains(item.title())) {
            score += 20;
        }
        return score;
    }

    private static boolean shareLongToken(String a, String b) {
        for (String token : a.split("\\s+")) {
            if (token.length() >= 4 && b.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String safePromptField(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
