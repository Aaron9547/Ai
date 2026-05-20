package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import java.util.ArrayList;
import java.util.List;

/** 子母分片：母块按语义节切分，子块在母块内固定长度切分。 */
public final class RagParentChildChunkSupport {

    /** 子块目标长度（字符）。 */
    public static final int DEFAULT_CHILD_CHARS = 360;

    private RagParentChildChunkSupport() {}

    public record ParentChildBlock(String parentText, List<String> childTexts) {}

    public static List<ParentChildBlock> split(String markdown, int parentMaxChars, int childChars) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        int parentSize = Math.max(400, parentMaxChars);
        int childSize = Math.max(200, childChars > 0 ? childChars : DEFAULT_CHILD_CHARS);
        List<String> parents =
                RagChunkSplitter.split(markdown.trim(), RagChunkStrategy.SEMANTIC, parentSize, 0);
        if (parents.isEmpty()) {
            parents = List.of(markdown.trim());
        }
        List<ParentChildBlock> out = new ArrayList<>();
        for (String parent : parents) {
            List<String> children =
                    RagChunkSplitter.split(parent, RagChunkStrategy.FIXED_CHAR, childSize, 0);
            if (children.isEmpty()) {
                children = List.of(parent);
            } else if (!RagChunkSplitter.chunksCoverSourceInOrder(parent, children)
                    || RagChunkSplitter.totalChunkChars(children) < parent.trim().length() * 85 / 100) {
                children = RagMarkdownFenceSupport.chunkByMaxChars(parent, childSize);
                if (children.isEmpty()) {
                    children = List.of(parent);
                }
            }
            out.add(new ParentChildBlock(parent, children));
        }
        return out;
    }

    /** 扁平预览：母块标题行 + 子块列表，供管理端预览。 */
    public static List<String> flattenForPreview(List<ParentChildBlock> blocks, int maxItems) {
        List<String> out = new ArrayList<>();
        int cap = maxItems > 0 ? maxItems : 48;
        for (ParentChildBlock b : blocks) {
            if (out.size() >= cap) {
                break;
            }
            String head = b.parentText();
            if (head.length() > 120) {
                head = head.substring(0, 120) + "…";
            }
            out.add("[母] " + head.replace('\n', ' ').trim());
            for (String c : b.childTexts()) {
                if (out.size() >= cap) {
                    return out;
                }
                out.add(c);
            }
        }
        return out;
    }
}
