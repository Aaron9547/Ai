package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagSiteLinkDiscoveryService;
import com.aaron.cloud.rag.crawl.fetch.BrowserFetcher;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsRenderDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final BrowserFetcher browserFetcher;
    private final RagSiteLinkDiscoveryService linkDiscoveryService;

    @Override
    public String id() {
        return "js_render_discovery";
    }

    @Override
    public boolean supports(String strategyId) {
        return "js_render_discovery".equalsIgnoreCase(strategyId);
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        EffectiveSiteCrawlPolicy.JsRenderPolicy jr = ctx.policy().discovery().jsRender();
        if (!jr.enabled()) {
            return List.of();
        }
        int threshold = jr.onlyWhenLinkCountBelow();
        if (threshold > 0) {
            int depth =
                    ctx.maxDepth() != null
                            ? ctx.maxDepth()
                            : ctx.policy().discovery().maxDepthDefault();
            int staticCount = linkDiscoveryService.countStaticArticleLinksOnEntry(ctx.baseUrl(), ctx.policy());
            if (staticCount >= threshold) {
                return List.of();
            }
        }
        return new ArrayList<>(browserFetcher.discoverLinks(ctx.baseUrl(), jr.maxPagesPerRun()));
    }
}
