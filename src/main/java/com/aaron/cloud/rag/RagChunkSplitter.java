package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import java.util.ArrayList;
import java.util.List;

/** 灏?Markdown/绾枃鏈垏鍒嗕负妫€绱㈠潡锛涚瓥鐣ヤ笌鐭ヨ瘑搴撻厤缃榻愩€?*/
public final class RagChunkSplitter {

    private RagChunkSplitter() {}

    public static List<String> split(
            String text, RagChunkStrategy strategy, int fixedChars, int slideOverlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = text.trim();
        return switch (strategy) {
            case NONE -> List.of(t);
            case FIXED_CHAR -> splitFixed(t, Math.max(200, fixedChars));
            case SEMANTIC -> splitSemantic(t, Math.max(200, fixedChars));
            case SLIDING_WINDOW -> splitSliding(t, Math.max(200, fixedChars), Math.max(0, slideOverlap));
            case CUSTOM -> splitFixed(t, Math.max(200, fixedChars));
        };
    }

    private static List<String> splitFixed(String t, int size) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < t.length(); i += size) {
            int end = Math.min(t.length(), i + size);
            String p = t.substring(i, end).trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out.isEmpty() ? List.of(t) : out;
    }

    private static List<String> splitSemantic(String t, int maxPara) {
        String[] paras = t.split("\\R{2,}");
        List<String> merged = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            String s = p.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (cur.length() + s.length() + 2 > maxPara && cur.length() > 0) {
                merged.add(cur.toString().trim());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) {
                cur.append("\n\n");
            }
            cur.append(s);
        }
        if (cur.length() > 0) {
            merged.add(cur.toString().trim());
        }
        List<String> out = new ArrayList<>();
        for (String m : merged) {
            if (m.length() <= maxPara) {
                out.add(m);
            } else {
                out.addAll(splitFixed(m, maxPara));
            }
        }
        return out.isEmpty() ? List.of(t) : out;
    }

    private static List<String> splitSliding(String t, int window, int overlap) {
        if (window <= overlap) {
            return splitFixed(t, window);
        }
        int step = window - overlap;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < t.length(); i += step) {
            int end = Math.min(t.length(), i + window);
            String p = t.substring(i, end).trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
            if (end >= t.length()) {
                break;
            }
        }
        return out.isEmpty() ? List.of(t) : out;
    }
}
