package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.rag.RagChunkStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 将 Markdown/纯文本切分为检索块；策略与知识库配置对齐。 */
public final class RagChunkSplitter {

    /** 语义分片仅按一级/二级标题切节，避免 ###/#### 把短文拆得过碎。 */
    private static final Pattern MARKDOWN_MAJOR_HEADING = Pattern.compile("(?m)^#{1,2}\\s+");

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
            case PARENT_CHILD ->
                    RagParentChildChunkSupport.flattenForPreview(
                            RagParentChildChunkSupport.split(
                                    t, Math.max(200, fixedChars), RagParentChildChunkSupport.DEFAULT_CHILD_CHARS),
                            64);
        };
    }

    private static List<String> splitFixed(String t, int size) {
        return RagMarkdownFenceSupport.chunkByMaxChars(t, size);
    }

    private static List<String> splitSemantic(String t, int maxPara) {
        List<String> sections = splitMarkdownSections(t);
        List<String> raw;
        if (sections.size() > 1) {
            raw = new ArrayList<>();
            for (String sec : sections) {
                raw.addAll(mergeParagraphs(sec, maxPara));
            }
            if (raw.isEmpty()) {
                raw = mergeParagraphs(t, maxPara);
            }
        } else {
            raw = mergeParagraphs(t, maxPara);
        }
        int minCoalesce = Math.max(400, maxPara / 3);
        List<String> result = coalesceSmallChunks(raw, minCoalesce, maxPara);
        if (!chunksCoverSourceInOrder(t, result)
                || totalChunkChars(result) < t.length() * 85 / 100) {
            return splitFixed(t, maxPara);
        }
        return result;
    }

    private static List<String> splitMarkdownSections(String t) {
        java.util.regex.Matcher m = MARKDOWN_MAJOR_HEADING.matcher(t);
        List<Integer> positions = new ArrayList<>();
        while (m.find()) {
            positions.add(m.start());
        }
        if (positions.isEmpty()) {
            return List.of(t);
        }
        List<String> out = new ArrayList<>();
        if (positions.get(0) > 0) {
            String lead = t.substring(0, positions.get(0)).trim();
            if (!lead.isEmpty()) {
                out.add(lead);
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            int from = positions.get(i);
            int to = i + 1 < positions.size() ? positions.get(i + 1) : t.length();
            String sec = t.substring(from, to).trim();
            if (!sec.isEmpty()) {
                out.add(sec);
            }
        }
        return out;
    }

    private static List<String> mergeParagraphs(String t, int maxPara) {
        List<RagMarkdownFenceSupport.Segment> segments = RagMarkdownFenceSupport.parseSegments(t);
        List<String> raw = new ArrayList<>();
        for (RagMarkdownFenceSupport.Segment seg : segments) {
            if (seg.kind() == RagMarkdownFenceSupport.Kind.FENCED) {
                raw.add(seg.content());
            } else {
                raw.addAll(mergeProseParagraphs(seg.content(), maxPara));
            }
        }
        return raw.isEmpty() ? List.of(t) : raw;
    }

    /** 正文段落合并（不含围栏）；围栏块由 {@link #mergeParagraphs} 单独保留。 */
    private static List<String> mergeProseParagraphs(String t, int maxPara) {
        String[] paras = t.split("\\R{2,}");
        List<String> merged = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            String s = p.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (isMajorMarkdownHeading(s) && cur.length() > 0) {
                merged.add(cur.toString().trim());
                cur = new StringBuilder();
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
                out.addAll(RagMarkdownFenceSupport.chunkByMaxChars(m, maxPara));
            }
        }
        return out.isEmpty() ? List.of(t) : out;
    }

    private static boolean isMajorMarkdownHeading(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return line.matches("^#{1,2}\\s+\\S.*");
    }

    /** 合并过短相邻块，减轻「一句一段」的碎片化。 */
    private static List<String> coalesceSmallChunks(List<String> chunks, int minSize, int maxSize) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            String c = chunk.trim();
            if (buf.isEmpty()) {
                buf.append(c);
                continue;
            }
            boolean smallPair = buf.length() < minSize || c.length() < minSize;
            boolean fencedPair =
                    RagMarkdownFenceSupport.looksLikeFencedMarkdown(buf.toString())
                            || RagMarkdownFenceSupport.looksLikeFencedMarkdown(c);
            if (smallPair && !fencedPair && buf.length() + c.length() + 2 <= maxSize) {
                buf.append("\n\n").append(c);
            } else {
                out.add(buf.toString());
                buf = new StringBuilder(c);
            }
        }
        if (!buf.isEmpty()) {
            out.add(buf.toString());
        }
        return out.isEmpty() ? chunks : out;
    }

    private static List<String> splitSliding(String t, int window, int overlap) {
        if (window <= overlap) {
            return splitFixed(t, window);
        }
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < t.length()) {
            int hardEnd = Math.min(t.length(), pos + window);
            int end = RagMarkdownFenceSupport.preferBreakEnd(t, pos, hardEnd);
            if (end <= pos) {
                end = hardEnd;
            }
            String p = t.substring(pos, end).trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
            if (end >= t.length()) {
                break;
            }
            int next = end - overlap;
            pos = next > pos ? next : end;
        }
        return out.isEmpty() ? List.of(t) : out;
    }

    /**
     * 校验分片是否按顺序覆盖原文（忽略仅空白差异）。供测试与排查「丢字」问题。
     */
    static boolean chunksCoverSourceInOrder(String source, List<String> chunks) {
        if (source == null || source.isBlank()) {
            return chunks == null || chunks.isEmpty();
        }
        String src = source.trim();
        int pos = 0;
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            String c = chunk.trim();
            int idx = src.indexOf(c, pos);
            if (idx < 0) {
                return false;
            }
            pos = idx + c.length();
        }
        return true;
    }

    /** 分片正文字符总数（trim 后），用于与原文长度粗比。 */
    static int totalChunkChars(List<String> chunks) {
        if (chunks == null) {
            return 0;
        }
        int n = 0;
        for (String c : chunks) {
            if (c != null && !c.isBlank()) {
                n += c.trim().length();
            }
        }
        return n;
    }
}
