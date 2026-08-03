package com.aaron.cloud.model.openai;

/**
 * 部分 OpenAI 兼容上游在 {@code delta.reasoning_content} 中发送累计全文而非增量；本类将其规范为增量片段。
 */
final class ReasoningDeltaNormalizer {

    private String cumulativeSeen = "";

    String toDelta(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (!cumulativeSeen.isEmpty() && raw.startsWith(cumulativeSeen)) {
            String delta = raw.substring(cumulativeSeen.length());
            cumulativeSeen = raw;
            return delta;
        }
        cumulativeSeen += raw;
        return raw;
    }
}
