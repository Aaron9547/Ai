package com.aaron.cloud.chat.websearch;

import java.util.concurrent.CompletableFuture;

/**
 * 对话流式联网：{@link #initialBundle()} 供主模型尽快开流；{@link #remainderFuture()} 在后台补全其余轮次并写缓存。
 */
public record WebSearchStreamGroundingSession(
        WebGroundingBundle initialBundle, CompletableFuture<WebGroundingBundle> remainderFuture) {

    public static WebSearchStreamGroundingSession completed(WebGroundingBundle bundle) {
        return new WebSearchStreamGroundingSession(bundle, CompletableFuture.completedFuture(bundle));
    }
}
