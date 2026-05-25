package com.aaron.cloud.rag;

/** 站点爬取/HTTP 拉取共用常量（对齐 ly-ai {@code JsoupHelper} 浏览器 UA）。 */
public final class RagWebCrawlConstants {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final int FETCH_TIMEOUT_MS = 45_000;
    public static final int DISCOVERY_TIMEOUT_MS = 15_000;

    private RagWebCrawlConstants() {}
}
