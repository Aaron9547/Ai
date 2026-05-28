package com.aaron.cloud.chat.dto;

/** 开放 API：会话内全部 LLM 调用的 token 计量合计。 */
public record ConversationTokenTotalView(int totalTokens) {}
