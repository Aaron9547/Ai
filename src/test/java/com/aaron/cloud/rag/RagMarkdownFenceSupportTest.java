package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.RagChunkStrategy;
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
