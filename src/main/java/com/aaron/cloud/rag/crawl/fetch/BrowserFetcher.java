package com.aaron.cloud.rag.crawl.fetch;

import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.discovery.DiscoveredUrl;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Playwright 可选：仅发现链接；未安装依赖时优雅降级为空列表。 */
@Slf4j
@Component
public class BrowserFetcher {

    private volatile boolean playwrightChecked;
    private volatile boolean playwrightAvailable;

    public List<DiscoveredUrl> discoverLinks(String baseUrl, int maxPages) {
        if (!isPlaywrightAvailable()) {
            log.warn("Playwright 不可用，跳过 js_render_discovery baseUrl={}", baseUrl);
            return List.of();
        }
        try {
            return discoverWithPlaywright(baseUrl, maxPages);
        } catch (Exception e) {
            log.warn("js_render_discovery 失败 baseUrl={} err={}", baseUrl, e.toString());
            return List.of();
        }
    }

    private synchronized boolean isPlaywrightAvailable() {
        if (!playwrightChecked) {
            playwrightChecked = true;
            try {
                Class.forName("com.microsoft.playwright.Playwright");
                playwrightAvailable = true;
            } catch (ClassNotFoundException e) {
                playwrightAvailable = false;
            }
        }
        return playwrightAvailable;
    }

    private List<DiscoveredUrl> discoverWithPlaywright(String baseUrl, int maxPages) {
        List<DiscoveredUrl> out = new ArrayList<>();
        int limit = Math.min(Math.max(maxPages, 1), 15);
        try (var playwright = com.microsoft.playwright.Playwright.create()) {
            var browser =
                    playwright.chromium().launch(
                            new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
            var page = browser.newPage();
            page.navigate(baseUrl);
            var links = page.evalOnSelectorAll("a[href]", "els => els.map(a => a.href)");
            if (links instanceof List<?> list) {
                int n = 0;
                for (Object o : list) {
                    if (n >= limit * 20) {
                        break;
                    }
                    if (o instanceof String href && href.startsWith("http")) {
                        out.add(new DiscoveredUrl(href, null, CrawlQueueRole.ARTICLE, "js_render_discovery"));
                        n++;
                    }
                }
            }
            browser.close();
        }
        return out;
    }
}
