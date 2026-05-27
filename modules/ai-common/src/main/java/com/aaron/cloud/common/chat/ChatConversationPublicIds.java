package com.aaron.cloud.common.chat;

import java.security.SecureRandom;

/** 开放 API 会话对外标识（非自增，避免枚举总量）。 */
public final class ChatConversationPublicIds {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ChatConversationPublicIds() {}

    /** 16 位小写字母数字，与 {@code chat_conversation.public_id} 列宽一致。 */
    public static String generate() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
