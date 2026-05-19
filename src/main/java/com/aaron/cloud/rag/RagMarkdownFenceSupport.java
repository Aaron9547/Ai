package com.aaron.cloud.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 围栏（{@code ```} / {@code ~~~}）保护：分片时不拆开围栏块，避免管理端渲染失败。
 */
public final class RagMarkdownFenceSupport {

    private static final Pattern OPENING_FENCE = Pattern.compile("^(`{3,}|~{3,})([^`~]*)\\s*$");

    public enum Kind {
        PROSE,
        FENCED
    }

    public record Segment(Kind kind, String content) {}

    private RagMarkdownFenceSupport() {}

    /** 将 Markdown 拆成「正文」与「完整围栏块」交替序列。 */
    public static List<Segment> parseSegments(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }
        List<Segment> out = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        StringBuilder prose = new StringBuilder();
        StringBuilder fence = new StringBuilder();
        boolean inFence = false;
        char fenceChar = 0;
        int fenceLen = 0;

        for (String line : lines) {
            if (!inFence) {
                var open = OPENING_FENCE.matcher(line.trim());
                if (open.matches()) {
                    flushProse(prose, out);
                    inFence = true;
                    fenceChar = open.group(1).charAt(0);
                    fenceLen = open.group(1).length();
                    fence.setLength(0);
                    fence.append(line).append('\n');
                } else {
                    if (!prose.isEmpty()) {
                        prose.append('\n');
                    }
                    prose.append(line);
                }
            } else {
                fence.append(line).append('\n');
                if (isClosingFenceLine(line, fenceChar, fenceLen)) {
                    out.add(new Segment(Kind.FENCED, fence.toString().stripTrailing()));
                    fence.setLength(0);
                    inFence = false;
                    fenceChar = 0;
                    fenceLen = 0;
                }
            }
        }
        if (inFence && !fence.isEmpty()) {
            out.add(new Segment(Kind.FENCED, fence.toString().stripTrailing()));
        } else {
            flushProse(prose, out);
        }
        return out;
    }

    /**
     * 按最大字符数打包；围栏块永不截断（单块超长时整段保留）。
     */
    public static List<String> chunkByMaxChars(String markdown, int maxChars) {
        int max = Math.max(200, maxChars);
        List<Segment> segments = parseSegments(markdown == null ? "" : markdown);
        if (segments.isEmpty()) {
            return markdown == null || markdown.isBlank() ? List.of() : List.of(markdown.trim());
        }
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (Segment seg : segments) {
            if (seg.kind() == Kind.FENCED) {
                flushBuffer(buf, out);
                out.add(seg.content());
                continue;
            }
            for (String para : splitProseParagraphs(seg.content())) {
                if (para.length() <= max) {
                    appendToBuffer(buf, out, para, max);
                } else {
                    flushBuffer(buf, out);
                    out.addAll(splitProseOversized(para, max));
                }
            }
        }
        flushBuffer(buf, out);
        return out.isEmpty() && markdown != null && !markdown.isBlank()
                ? List.of(markdown.trim())
                : out;
    }

    private static void flushProse(StringBuilder prose, List<Segment> out) {
        if (prose.isEmpty()) {
            return;
        }
        String p = prose.toString();
        if (!p.isBlank()) {
            out.add(new Segment(Kind.PROSE, p));
        }
        prose.setLength(0);
    }

    private static boolean isClosingFenceLine(String line, char markerChar, int markerLen) {
        String t = line.trim();
        if (t.isEmpty()) {
            return false;
        }
        int i = 0;
        while (i < t.length() && t.charAt(i) == markerChar) {
            i++;
        }
        return i >= markerLen && t.substring(i).isBlank();
    }

    private static List<String> splitProseParagraphs(String prose) {
        if (prose == null || prose.isBlank()) {
            return List.of();
        }
        String[] parts = prose.split("\\R{2,}");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out.isEmpty() ? List.of(prose.trim()) : out;
    }

    private static void appendToBuffer(StringBuilder buf, List<String> out, String piece, int max) {
        if (buf.isEmpty()) {
            if (piece.length() <= max) {
                buf.append(piece);
            } else {
                out.add(piece);
            }
            return;
        }
        int need = buf.length() + 2 + piece.length();
        if (need <= max) {
            buf.append("\n\n").append(piece);
        } else {
            flushBuffer(buf, out);
            if (piece.length() <= max) {
                buf.append(piece);
            } else {
                out.add(piece);
            }
        }
    }

    private static void flushBuffer(StringBuilder buf, List<String> out) {
        if (!buf.isEmpty()) {
            out.add(buf.toString().trim());
            buf.setLength(0);
        }
    }

    /** 仅对正文做固定长度切分（调用方保证无围栏）。 */
    private static List<String> splitProseOversized(String prose, int size) {
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < prose.length()) {
            int hardEnd = Math.min(prose.length(), pos + size);
            int end = preferProseBreakEnd(prose, pos, hardEnd);
            String p = prose.substring(pos, end).trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
            if (end >= prose.length()) {
                break;
            }
            pos = end;
        }
        return out.isEmpty() ? List.of(prose.trim()) : out;
    }

    private static int preferProseBreakEnd(String t, int start, int hardEnd) {
        if (hardEnd >= t.length()) {
            return hardEnd;
        }
        int minPos = start + Math.max(1, (hardEnd - start) / 2);
        int p = t.lastIndexOf("\n\n", hardEnd - 1);
        if (p >= minPos) {
            return p + 2;
        }
        p = t.lastIndexOf('\n', hardEnd - 1);
        if (p >= minPos) {
            return p + 1;
        }
        for (int i = hardEnd - 1; i >= minPos; i--) {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        p = t.lastIndexOf(' ', hardEnd - 1);
        if (p >= minPos) {
            return p + 1;
        }
        return hardEnd;
    }

    static boolean looksLikeFencedMarkdown(String chunk) {
        if (chunk == null) {
            return false;
        }
        String t = chunk.trim();
        return t.startsWith("```") || t.startsWith("~~~");
    }

    /** 若断点落在围栏内，则延伸到围栏结尾，避免截断代码块。 */
    public static int adjustBreakOutsideFence(String markdown, int breakPos) {
        if (markdown == null || breakPos <= 0 || breakPos >= markdown.length()) {
            return breakPos;
        }
        int searchFrom = 0;
        for (Segment seg : parseSegments(markdown)) {
            if (seg.kind() != Kind.FENCED) {
                searchFrom = markdown.indexOf(seg.content(), searchFrom);
                if (searchFrom < 0) {
                    break;
                }
                searchFrom += seg.content().length();
                continue;
            }
            int idx = markdown.indexOf(seg.content(), searchFrom);
            if (idx < 0) {
                break;
            }
            int end = idx + seg.content().length();
            if (breakPos > idx && breakPos < end) {
                return end;
            }
            searchFrom = end;
        }
        return breakPos;
    }

    public static int preferBreakEnd(String t, int start, int hardEnd) {
        if (hardEnd >= t.length()) {
            return t.length();
        }
        int minPos = start + Math.max(1, (hardEnd - start) / 2);
        int p = t.lastIndexOf("\n\n", hardEnd - 1);
        if (p >= minPos) {
            return adjustBreakOutsideFence(t, p + 2);
        }
        p = t.lastIndexOf('\n', hardEnd - 1);
        if (p >= minPos) {
            return adjustBreakOutsideFence(t, p + 1);
        }
        for (int i = hardEnd - 1; i >= minPos; i--) {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return adjustBreakOutsideFence(t, i + 1);
            }
        }
        p = t.lastIndexOf(' ', hardEnd - 1);
        if (p >= minPos) {
            return adjustBreakOutsideFence(t, p + 1);
        }
        return adjustBreakOutsideFence(t, hardEnd);
    }
}
