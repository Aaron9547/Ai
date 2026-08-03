package com.aaron.cloud.rag.crawl.policy;

import com.aaron.cloud.common.api.enums.rag.RagSiteCrawlDiscoveryStrategy;
import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.common.rag.crawl.SiteCrawlRuntimeValidator;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.rag.RagWebCrawlExtractConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析租户 + 单站有效爬取策略。单站 {@link RagWebCrawlExtractConfig} 仅允许加严礼貌（不可比租户更激进）。
 */
@Component
@RequiredArgsConstructor
public class SiteCrawlPolicyResolver {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final AiRagProperties aiRagProperties;
    private final ObjectMapper objectMapper;

    public EffectiveSiteCrawlPolicy resolve(long tenantId) {
        return resolve(tenantId, null);
    }

    public EffectiveSiteCrawlPolicy resolve(long tenantId, RagWebCrawlExtractConfig siteConfig) {
        SiteCrawlPreset preset = tenantRuntimeSettingApplicationService.siteCrawlPreset(tenantId);
        String json = tenantRuntimeSettingApplicationService.siteCrawlRuntimeJson(tenantId);
        JsonNode patch = SiteCrawlRuntimeValidator.parseObject(json, objectMapper);
        EffectiveSiteCrawlPolicy base =
                preset == SiteCrawlPreset.CUSTOM
                        ? policyFromJson(preset, patch)
                        : mergeTemplateWithPatch(preset, patch);
        if (siteConfig != null) {
            base = applySiteOverrides(base, siteConfig);
        }
        return applySafetyClamp(base);
    }

    private EffectiveSiteCrawlPolicy mergeTemplateWithPatch(SiteCrawlPreset preset, JsonNode patch) {
        JsonNode templateJson = policyToJson(SiteCrawlPresetTemplates.template(preset));
        JsonNode merged = SiteCrawlRuntimeValidator.mergePatch(templateJson, patch);
        return policyFromJson(preset, merged);
    }

    private EffectiveSiteCrawlPolicy applySiteOverrides(
            EffectiveSiteCrawlPolicy base, RagWebCrawlExtractConfig site) {
        RagWebCrawlExtractConfig.SiteCrawlDiscoveryOverride d = site.getDiscovery();
        RagWebCrawlExtractConfig.SiteCrawlPolitenessOverride p = site.getPoliteness();
        EffectiveSiteCrawlPolicy.DiscoveryPolicy discovery = base.discovery();
        EffectiveSiteCrawlPolicy.PolitenessPolicy politeness = base.politeness();
        if (d != null) {
            if (d.getStrategies() != null && !d.getStrategies().isEmpty()) {
                List<String> filtered =
                        d.getStrategies().stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty() && RagSiteCrawlDiscoveryStrategy.ALL_IDS.contains(s))
                                .distinct()
                                .toList();
                if (!filtered.isEmpty()) {
                    discovery =
                            new EffectiveSiteCrawlPolicy.DiscoveryPolicy(
                                    filtered,
                                    d.getMaxDepth() != null ? d.getMaxDepth() : discovery.maxDepthDefault(),
                                    discovery.maxExplorePages(),
                                    discovery.maxPaginationPerList(),
                                    discovery.maxSitemapSeeds(),
                                    discovery.maxArticlesPerRun(),
                                    discovery.jsRender(),
                                    discovery.urlMerge());
                }
            }
            if (d.getMaxDepth() != null && (d.getStrategies() == null || d.getStrategies().isEmpty())) {
                discovery =
                        new EffectiveSiteCrawlPolicy.DiscoveryPolicy(
                                discovery.strategies(),
                                d.getMaxDepth(),
                                discovery.maxExplorePages(),
                                discovery.maxPaginationPerList(),
                                discovery.maxSitemapSeeds(),
                                discovery.maxArticlesPerRun(),
                                discovery.jsRender(),
                                discovery.urlMerge());
            }
            if (d.getJsRender() != null) {
                var jr = d.getJsRender();
                discovery =
                        new EffectiveSiteCrawlPolicy.DiscoveryPolicy(
                                discovery.strategies(),
                                discovery.maxDepthDefault(),
                                discovery.maxExplorePages(),
                                discovery.maxPaginationPerList(),
                                discovery.maxSitemapSeeds(),
                                discovery.maxArticlesPerRun(),
                                new EffectiveSiteCrawlPolicy.JsRenderPolicy(
                                        Boolean.TRUE.equals(jr.getEnabled()),
                                        jr.getMaxPagesPerRun() != null
                                                ? jr.getMaxPagesPerRun()
                                                : discovery.jsRender().maxPagesPerRun(),
                                        jr.getOnlyWhenLinkCountBelow() != null
                                                ? jr.getOnlyWhenLinkCountBelow()
                                                : discovery.jsRender().onlyWhenLinkCountBelow()),
                                discovery.urlMerge());
            }
        }
        if (p != null) {
            politeness = tightenPoliteness(politeness, p, site.getPresetLock());
        }
        return new EffectiveSiteCrawlPolicy(
                base.preset(), discovery, politeness, base.fetch(), base.extract(), base.ingest());
    }

    private static EffectiveSiteCrawlPolicy.PolitenessPolicy tightenPoliteness(
            EffectiveSiteCrawlPolicy.PolitenessPolicy base,
            RagWebCrawlExtractConfig.SiteCrawlPolitenessOverride site,
            String presetLock) {
        double qps = base.perHostQps();
        int hostConc = base.perHostConcurrency();
        int global = base.globalConcurrency();
        if (site.getPerHostQps() != null) {
            qps = Math.min(qps, site.getPerHostQps());
        }
        if (site.getPerHostConcurrency() != null) {
            hostConc = Math.min(hostConc, site.getPerHostConcurrency());
        }
        if (site.getGlobalConcurrency() != null) {
            global = Math.min(global, site.getGlobalConcurrency());
        }
        if (presetLock != null && !presetLock.isBlank()) {
            EffectiveSiteCrawlPolicy floor =
                    SiteCrawlPresetTemplates.template(SiteCrawlPreset.fromStorage(presetLock));
            qps = Math.min(qps, floor.politeness().perHostQps());
            hostConc = Math.min(hostConc, floor.politeness().perHostConcurrency());
            global = Math.min(global, floor.politeness().globalConcurrency());
        }
        return new EffectiveSiteCrawlPolicy.PolitenessPolicy(
                true,
                qps,
                hostConc,
                global,
                base.jitterMsMin(),
                base.jitterMsMax(),
                base.retryMax(),
                base.backoffBaseMs(),
                base.backoffMaxMs(),
                base.dailyMaxRequestsPerHost());
    }

    private EffectiveSiteCrawlPolicy applySafetyClamp(EffectiveSiteCrawlPolicy p) {
        int maxBody = Math.min(p.fetch().maxBodyBytes(), 5_242_880);
        double qps = Math.min(p.politeness().perHostQps(), 2.0);
        int global = Math.min(p.politeness().globalConcurrency(), 64);
        int host = Math.min(p.politeness().perHostConcurrency(), 8);
        var d = p.discovery();
        var jr = d.jsRender();
        int jsPages = jr.enabled() ? Math.min(15, Math.max(0, jr.maxPagesPerRun())) : jr.maxPagesPerRun();
        int maxArticles = d.maxArticlesPerRun();
        if (maxArticles > 0) {
            maxArticles = Math.min(maxArticles, 20_000);
        }
        var discovery =
                new EffectiveSiteCrawlPolicy.DiscoveryPolicy(
                        d.strategies(),
                        d.maxDepthDefault(),
                        d.maxExplorePages(),
                        d.maxPaginationPerList(),
                        d.maxSitemapSeeds(),
                        maxArticles,
                        new EffectiveSiteCrawlPolicy.JsRenderPolicy(
                                jr.enabled(), jsPages, jr.onlyWhenLinkCountBelow()),
                        d.urlMerge());
        return new EffectiveSiteCrawlPolicy(
                p.preset(),
                discovery,
                new EffectiveSiteCrawlPolicy.PolitenessPolicy(
                        true,
                        qps,
                        host,
                        global,
                        p.politeness().jitterMsMin(),
                        p.politeness().jitterMsMax(),
                        p.politeness().retryMax(),
                        p.politeness().backoffBaseMs(),
                        p.politeness().backoffMaxMs(),
                        p.politeness().dailyMaxRequestsPerHost()),
                new EffectiveSiteCrawlPolicy.FetchPolicy(
                        maxBody,
                        p.fetch().timeoutMs(),
                        p.fetch().metaRefreshMaxHops(),
                        p.fetch().conditionalRequest(),
                        p.fetch().sharedHttpClient()),
                p.extract(),
                p.ingest());
    }

    private EffectiveSiteCrawlPolicy policyFromJson(SiteCrawlPreset preset, JsonNode n) {
        SiteCrawlPreset p =
                n.has("preset") ? SiteCrawlPreset.fromStorage(n.path("preset").asText(preset.name())) : preset;
        JsonNode d = n.path("discovery");
        List<String> strategies =
                d.has("strategies")
                        ? SiteCrawlRuntimeValidator.strategiesFromJson(d)
                        : SiteCrawlPresetTemplates.template(p).discovery().strategies();
        if (strategies.isEmpty()) {
            strategies = SiteCrawlPresetTemplates.template(p).discovery().strategies();
        }
        var discovery =
                new EffectiveSiteCrawlPolicy.DiscoveryPolicy(
                        strategies,
                        d.path("maxDepthDefault").asInt(SiteCrawlPresetTemplates.template(p).discovery().maxDepthDefault()),
                        d.path("maxExplorePages").asInt(SiteCrawlPresetTemplates.template(p).discovery().maxExplorePages()),
                        d.path("maxPaginationPerList")
                                .asInt(SiteCrawlPresetTemplates.template(p).discovery().maxPaginationPerList()),
                        d.path("maxSitemapSeeds").asInt(SiteCrawlPresetTemplates.template(p).discovery().maxSitemapSeeds()),
                        d.path("maxArticlesPerRun")
                                .asInt(SiteCrawlPresetTemplates.template(p).discovery().maxArticlesPerRun()),
                        jsFromJson(d.path("jsRender"), p),
                        urlMergeFromJson(d.path("urlMerge"), p));
        JsonNode pol = n.path("politeness");
        var politeness =
                new EffectiveSiteCrawlPolicy.PolitenessPolicy(
                        true,
                        pol.path("perHostQps").asDouble(SiteCrawlPresetTemplates.template(p).politeness().perHostQps()),
                        pol.path("perHostConcurrency")
                                .asInt(SiteCrawlPresetTemplates.template(p).politeness().perHostConcurrency()),
                        pol.path("globalConcurrency")
                                .asInt(SiteCrawlPresetTemplates.template(p).politeness().globalConcurrency()),
                        pol.path("jitterMsMin").asInt(SiteCrawlPresetTemplates.template(p).politeness().jitterMsMin()),
                        pol.path("jitterMsMax").asInt(SiteCrawlPresetTemplates.template(p).politeness().jitterMsMax()),
                        pol.path("retryMax").asInt(SiteCrawlPresetTemplates.template(p).politeness().retryMax()),
                        pol.path("backoffBaseMs").asLong(SiteCrawlPresetTemplates.template(p).politeness().backoffBaseMs()),
                        pol.path("backoffMaxMs").asLong(SiteCrawlPresetTemplates.template(p).politeness().backoffMaxMs()),
                        pol.path("dailyMaxRequestsPerHost")
                                .asInt(SiteCrawlPresetTemplates.template(p).politeness().dailyMaxRequestsPerHost()));
        JsonNode f = n.path("fetch");
        var fetch =
                new EffectiveSiteCrawlPolicy.FetchPolicy(
                        f.path("maxBodyBytes").asInt(SiteCrawlPresetTemplates.template(p).fetch().maxBodyBytes()),
                        f.path("timeoutMs").asInt(SiteCrawlPresetTemplates.template(p).fetch().timeoutMs()),
                        f.path("metaRefreshMaxHops").asInt(SiteCrawlPresetTemplates.template(p).fetch().metaRefreshMaxHops()),
                        f.path("conditionalRequest")
                                .asBoolean(SiteCrawlPresetTemplates.template(p).fetch().conditionalRequest()),
                        f.path("sharedHttpClient").asBoolean(SiteCrawlPresetTemplates.template(p).fetch().sharedHttpClient()));
        JsonNode ex = n.path("extract");
        var extract =
                new EffectiveSiteCrawlPolicy.ExtractPolicy(
                        ex.path("defaultExtractor")
                                .asText(
                                        aiRagProperties.getSiteCrawl().getContentExtractor() != null
                                                ? aiRagProperties.getSiteCrawl().getContentExtractor()
                                                : "jsoup"),
                        ex.path("readabilityFallbackMinLen")
                                .asInt(SiteCrawlPresetTemplates.template(p).extract().readabilityFallbackMinLen()),
                        ex.path("readabilityFallbackEnabled")
                                .asBoolean(SiteCrawlPresetTemplates.template(p).extract().readabilityFallbackEnabled()));
        JsonNode ing = n.path("ingest");
        var ingest =
                new EffectiveSiteCrawlPolicy.IngestPolicy(
                        ing.path("minMarkdownChars").asInt(SiteCrawlPresetTemplates.template(p).ingest().minMarkdownChars()),
                        ing.path("rejectGarbled").asBoolean(SiteCrawlPresetTemplates.template(p).ingest().rejectGarbled()),
                        ing.path("rejectRedirectShellOnly")
                                .asBoolean(SiteCrawlPresetTemplates.template(p).ingest().rejectRedirectShellOnly()));
        return new EffectiveSiteCrawlPolicy(p, discovery, politeness, fetch, extract, ingest);
    }

    private static EffectiveSiteCrawlPolicy.JsRenderPolicy jsFromJson(JsonNode n, SiteCrawlPreset p) {
        var d = SiteCrawlPresetTemplates.template(p).discovery().jsRender();
        if (n == null || n.isMissingNode()) {
            return d;
        }
        return new EffectiveSiteCrawlPolicy.JsRenderPolicy(
                n.path("enabled").asBoolean(d.enabled()),
                n.path("maxPagesPerRun").asInt(d.maxPagesPerRun()),
                n.path("onlyWhenLinkCountBelow").asInt(d.onlyWhenLinkCountBelow()));
    }

    private static EffectiveSiteCrawlPolicy.UrlMergePolicy urlMergeFromJson(JsonNode n, SiteCrawlPreset p) {
        var d = SiteCrawlPresetTemplates.template(p).discovery().urlMerge();
        if (n == null || n.isMissingNode()) {
            return d;
        }
        return new EffectiveSiteCrawlPolicy.UrlMergePolicy(
                n.path("boostMultiSource").asBoolean(d.boostMultiSource()),
                n.path("minSourcesForBoost").asInt(d.minSourcesForBoost()));
    }

    public JsonNode policyToJson(EffectiveSiteCrawlPolicy policy) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("preset", policy.preset().name());
        root.set("discovery", objectMapper.valueToTree(policy.discovery()));
        root.set("politeness", objectMapper.valueToTree(policy.politeness()));
        root.set("fetch", objectMapper.valueToTree(policy.fetch()));
        root.set("extract", objectMapper.valueToTree(policy.extract()));
        root.set("ingest", objectMapper.valueToTree(policy.ingest()));
        return root;
    }

    public String defaultRuntimeJsonForPreset(SiteCrawlPreset preset) {
        return policyToJson(SiteCrawlPresetTemplates.template(preset)).toString();
    }
}
