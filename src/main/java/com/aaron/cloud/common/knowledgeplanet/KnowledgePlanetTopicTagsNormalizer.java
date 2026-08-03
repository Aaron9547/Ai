package com.aaron.cloud.common.knowledgeplanet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 沉淀 topicTags 顺序校正：具体领域必须在 {@code [0]}，宽范畴修饰通过后缀/占位模式识别并移到尾部。
 * 仅用于排序，不参与跨主题并簇。
 */
@Slf4j
public final class KnowledgePlanetTopicTagsNormalizer {

    private static final String[] BROAD_SUFFIXES = {
        "科普", "常识", "概论", "概述", "入门", "杂谈", "综合", "简介", "基础知识"
    };

    /** 极短占位标签，仅用于把头部位移到尾部。 */
    private static final Set<String> PLACEHOLDER_TAGS =
            Set.of("其他", "通用", "杂项", "百科", "通识科普");

    private KnowledgePlanetTopicTagsNormalizer() {}

    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String t : raw) {
            if (t == null) {
                continue;
            }
            String trimmed = t.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }
            tags.add(trimmed);
        }
        if (tags.size() < 2) {
            return List.copyOf(tags);
        }
        List<String> work = new ArrayList<>(tags);
        int guard = 0;
        while (work.size() >= 2
                && isBroadModifierTag(work.getFirst())
                && guard++ < work.size()) {
            String head = work.removeFirst();
            work.add(head);
        }
        if (!work.isEmpty() && isBroadModifierTag(work.getFirst())) {
            log.debug("[知识星球] topicTags 全部为宽修饰，保留原顺序: {}", work);
        }
        return List.copyOf(work);
    }

    public static boolean isBroadModifierTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        String t = tag.trim();
        for (String placeholder : PLACEHOLDER_TAGS) {
            if (placeholder.equalsIgnoreCase(t)) {
                return true;
            }
        }
        String lower = t.toLowerCase(Locale.ROOT);
        for (String suffix : BROAD_SUFFIXES) {
            if (lower.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
