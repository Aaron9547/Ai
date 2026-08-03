package com.aaron.cloud.common.api.dto.model;

/**
 * OpenAI 兼容 Chat Completions 的 usage 字段（含豆包等最后一帧 SSE 返回）。
 */
public record ModelTokenUsage(int promptTokens, int completionTokens, int totalTokens) {}
