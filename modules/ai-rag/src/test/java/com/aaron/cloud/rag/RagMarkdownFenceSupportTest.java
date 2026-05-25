package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aaron.cloud.common.api.enums.rag.RagChunkStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagMarkdownFenceSupportTest {

    @Test
    void parseSegments_keepsFenceWithBlankLinesInside() {
        String md =
                """
                intro

                ```java
                line1

                line2
                ```

                outro
                """;
        var segs = RagMarkdownFenceSupport.parseSegments(md);
        long fenced = segs.stream().filter(s -> s.kind() == RagMarkdownFenceSupport.Kind.FENCED).count();
        assertEquals(1, fenced);
        String fence = segs.stream()
                .filter(s -> s.kind() == RagMarkdownFenceSupport.Kind.FENCED)
                .findFirst()
                .orElseThrow()
                .content();
        assertTrue(fence.startsWith("```java"));
        assertTrue(fence.endsWith("```"));
        assertTrue(fence.contains("line1"));
        assertTrue(fence.contains("line2"));
    }

    @Test
    void chunkByMaxChars_neverSplitsFenceMarkers() {
        String md =
                """
                text before

                ```csharp
                Console.WriteLine("hi");

                var x = 1;
                ```

                text after
                """;
        List<String> chunks = RagMarkdownFenceSupport.chunkByMaxChars(md, 80);
        for (String c : chunks) {
            if (c.contains("```")) {
                assertTrue(
                        c.trim().startsWith("```") && c.trim().endsWith("```"),
                        "fence chunk must be complete: " + c);
            }
        }
    }

    @Test
    void semanticSplit_preservesCodeFence() {
        String md =
                """
                ## Section

                paragraph one.

                ```js
                function a() {
                  return 1;
                }
                ```

                paragraph two.
                """;
        List<String> parts = RagChunkSplitter.split(md, RagChunkStrategy.SEMANTIC, 200, 0);
        boolean hasCompleteFence =
                parts.stream()
                        .anyMatch(
                                p ->
                                        p.trim().startsWith("```js")
                                                && p.trim().endsWith("```")
                                                && p.contains("function a()"));
        assertTrue(hasCompleteFence, "expected one chunk with full fence, got: " + parts);
    }

    @Test
    void fixedChar_chunksCoverSourceWithTableAndTasks() {
        String md =
                """
                ## 说明

                | 列 A | 列 B |
                | --- | --- |
                | 一 | 二 |

                - [ ] 待办一
                - [x] 已完成

                正文段落，包含足够长度以便触发二次切分。正文段落，包含足够长度以便触发二次切分。
                正文段落，包含足够长度以便触发二次切分。正文段落，包含足够长度以便触发二次切分。
                """;
        List<String> parts = RagChunkSplitter.split(md, RagChunkStrategy.FIXED_CHAR, 120, 0);
        assertFalse(parts.isEmpty());
        assertTrue(RagChunkSplitter.chunksCoverSourceInOrder(md, parts), "chunks: " + parts);
        assertTrue(
                RagChunkSplitter.totalChunkChars(parts) >= md.trim().length() * 85 / 100,
                "total chunk chars too small vs source");
    }

    @Test
    void slidingWindow_doesNotSkipMiddleCharacters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        String t = sb.toString();
        List<String> parts = RagChunkSplitter.split(t, RagChunkStrategy.SLIDING_WINDOW, 100, 20);
        assertTrue(RagChunkSplitter.chunksCoverSourceInOrder(t, parts), "sliding must cover all chars in order");
    }

    @Test
    void parentChild_childChunksPreserveFence() {
        String md =
                """
                ## Title

                some text.

                ```python
                def f():
                    pass
                ```
                """;
        var blocks = RagParentChildChunkSupport.split(md, 800, 200);
        assertTrue(!blocks.isEmpty());
        String allChildren =
                String.join("\n---\n", blocks.stream().flatMap(b -> b.childTexts().stream()).toList());
        if (allChildren.contains("```")) {
            assertTrue(
                    allChildren.contains("```python") && allChildren.contains("def f()"),
                    "child chunks should not orphan fence: " + allChildren);
        }
    }
}
