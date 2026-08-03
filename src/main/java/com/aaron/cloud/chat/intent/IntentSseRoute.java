package com.aaron.cloud.chat.intent;

import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 意图 SSE 路由结果：发射器 + 命中摘要（用于回写用户 meta 与统计）。 */
public record IntentSseRoute(SseEmitter emitter, ChatIntentDefinition definition, IntentKeywordMatchHit keywordHit) {}
