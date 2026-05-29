package com.aaron.cloud.chat;

/** 深度思考流护栏：限制总长度并检测模型 reasoning 重复循环。 */
public final class ChatReasoningStreamGuard {

    public enum Verdict {
        CONTINUE,
        STOP_LENGTH,
        STOP_REPETITION
    }

    /** 与常见模型 max completion 同量级，避免 reasoning 独占整段输出配额。 */
    static final int DEFAULT_MAX_CHARS = 24_000;

    private static final int MIN_LEN_FOR_REPEAT = 2_400;
    private static final int REPEAT_BLOCK_CHARS = 480;
    private static final int REPEAT_MIN_OCCURRENCES = 3;

    private final int maxChars;
    private final StringBuilder accumulated = new StringBuilder();

    public ChatReasoningStreamGuard() {
        this(DEFAULT_MAX_CHARS);
    }

    ChatReasoningStreamGuard(int maxChars) {
        this.maxChars = Math.max(4_000, maxChars);
    }

    public Verdict appendDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return Verdict.CONTINUE;
        }
        accumulated.append(delta);
        if (accumulated.length() > maxChars) {
            return Verdict.STOP_LENGTH;
        }
        if (detectRepetition()) {
            return Verdict.STOP_REPETITION;
        }
        return Verdict.CONTINUE;
    }

    public int length() {
        return accumulated.length();
    }

    private boolean detectRepetition() {
        int len = accumulated.length();
        if (len < MIN_LEN_FOR_REPEAT) {
            return false;
        }
        int block = Math.min(REPEAT_BLOCK_CHARS, len / 5);
        if (block < 80) {
            return false;
        }
        String suffix = accumulated.substring(len - block, len);
        int windowStart = Math.max(0, len - block * REPEAT_MIN_OCCURRENCES * 2);
        String window = accumulated.substring(windowStart, len - block);
        int count = 0;
        int from = 0;
        while (true) {
            int idx = window.indexOf(suffix, from);
            if (idx < 0) {
                break;
            }
            count++;
            if (count >= REPEAT_MIN_OCCURRENCES - 1) {
                return true;
            }
            from = idx + Math.max(1, block / 4);
        }
        return false;
    }
}
