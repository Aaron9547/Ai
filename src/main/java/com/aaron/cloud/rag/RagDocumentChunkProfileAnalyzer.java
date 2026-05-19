package com.aaron.cloud.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 判断上传类文档是否适合推荐子母分片（长文 + 多层级标题 / 政策手册特征）。 */
public final class RagDocumentChunkProfileAnalyzer {

    private static final int MIN_CHARS_LONG_DOC = 8_000;
    private static final int MIN_CHARS_MEDIUM_DOC = 6_000;
    private static final Pattern MAJOR_HEADING = Pattern.compile("(?m)^#{1,2}\\s+\\S");
    private static final Pattern MINOR_HEADING = Pattern.compile("(?m)^#{3,6}\\s+\\S");
    private static final Pattern POLICY_HINT =
            Pattern.compile(
                    "(政策|办法|规定|条例|细则|手册|指南|章程|制度|管理规定|实施办法|办事指南)",
                    Pattern.CASE_INSENSITIVE);

    private RagDocumentChunkProfileAnalyzer() {}

    public record IngestAnalyzeResult(
            boolean suggestParentChild,
            int charCount,
            int majorHeadingCount,
            int minorHeadingCount,
            List<String> reasons) {}

    public static IngestAnalyzeResult analyze(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new IngestAnalyzeResult(false, 0, 0, 0, List.of());
        }
        String t = markdown.trim();
        int chars = t.length();
        int major = countMatches(MAJOR_HEADING, t);
        int minor = countMatches(MINOR_HEADING, t);
        boolean longDoc = chars >= MIN_CHARS_LONG_DOC;
        boolean mediumDoc = chars >= MIN_CHARS_MEDIUM_DOC;
        boolean multiLevel = major >= 2 && major + minor >= 4;
        boolean richHeadings = major >= 3;
        boolean policyLike = POLICY_HINT.matcher(t).find();

        List<String> reasons = new ArrayList<>();
        if (longDoc) {
            reasons.add("正文较长（约 " + chars + " 字）");
        } else if (mediumDoc && (multiLevel || policyLike)) {
            reasons.add("正文篇幅中等且结构较复杂（约 " + chars + " 字）");
        }
        if (multiLevel || richHeadings) {
            reasons.add("含多层级 Markdown 标题（一级/二级 " + major + " 处，三级及以下 " + minor + " 处）");
        }
        if (policyLike) {
            reasons.add("内容形态接近政策、制度或手册类文档");
        }

        boolean suggest =
                (longDoc && (multiLevel || richHeadings || policyLike))
                        || (mediumDoc && multiLevel && policyLike);

        return new IngestAnalyzeResult(suggest, chars, major, minor, List.copyOf(reasons));
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
