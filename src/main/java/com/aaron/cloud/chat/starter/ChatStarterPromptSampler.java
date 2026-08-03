package com.aaron.cloud.chat.starter;

import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

final class ChatStarterPromptSampler {

    private ChatStarterPromptSampler() {}

    static List<ChatStarterPrompt> sample(
            List<ChatStarterPrompt> pool, int limit, Set<Long> excludeIds) {
        if (pool == null || pool.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<ChatStarterPrompt> candidates =
                pool.stream()
                        .filter(p -> excludeIds == null || !excludeIds.contains(p.getId()))
                        .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ChatStarterPrompt> bag = new ArrayList<>(candidates);
        List<ChatStarterPrompt> picked = new ArrayList<>(Math.min(limit, bag.size()));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (!bag.isEmpty() && picked.size() < limit) {
            int totalWeight = 0;
            for (ChatStarterPrompt p : bag) {
                int w = p.getWeight() == null || p.getWeight() <= 0 ? 1 : p.getWeight();
                totalWeight += w;
            }
            int roll = rnd.nextInt(totalWeight);
            int acc = 0;
            ChatStarterPrompt chosen = bag.get(bag.size() - 1);
            for (ChatStarterPrompt p : bag) {
                int w = p.getWeight() == null || p.getWeight() <= 0 ? 1 : p.getWeight();
                acc += w;
                if (roll < acc) {
                    chosen = p;
                    break;
                }
            }
            picked.add(chosen);
            bag.remove(chosen);
        }
        return Collections.unmodifiableList(picked);
    }
}
