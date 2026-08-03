package com.aaron.cloud.chat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话级「用户主动取消生成」登记：SSE 客户端断连不写入此表；仅 {@link #requestCancel}（C 端停止按钮）置位，
 * 供 {@link com.aaron.cloud.common.api.dto.model.ModelChatRequest#getStreamCancelled()} 中断上游。
 */
@Slf4j
@Component
public class ChatStreamCancelRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> active = new ConcurrentHashMap<>();

    public AtomicBoolean register(long tenantId, long conversationId) {
        AtomicBoolean flag = new AtomicBoolean(false);
        active.put(key(tenantId, conversationId), flag);
        return flag;
    }

    public void requestCancel(long tenantId, long conversationId) {
        AtomicBoolean flag = active.get(key(tenantId, conversationId));
        if (flag != null && flag.compareAndSet(false, true)) {
            log.info("[对话] 用户主动取消生成：租户 {}，会话 {}", tenantId, conversationId);
        }
    }

    /** 流式线程结束时移除登记；{@code remove(key, flag)} 避免覆盖同会话新一轮生成。 */
    public void unregister(long tenantId, long conversationId, AtomicBoolean flag) {
        active.remove(key(tenantId, conversationId), flag);
    }

    private static String key(long tenantId, long conversationId) {
        return tenantId + ":" + conversationId;
    }
}
