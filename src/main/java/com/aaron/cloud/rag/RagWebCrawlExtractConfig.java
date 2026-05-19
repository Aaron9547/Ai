package com.aaron.cloud.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 站点级网页正文抽取配置（存 rag_web_crawl_site.extract_config JSON）。 */
@Data
public class RagWebCrawlExtractConfig {

    /** jsoup（默认）或 readability。 */
    private String extractor;

    /** 正文 CSS 选择器，优先于全局 article/main 规则。 */
    private String contentSelector;

    /** 额外剔除区域。 */
    private List<String> excludeSelectors = new ArrayList<>();

    /** 标题 CSS 选择器，优先于 og:title。 */
    private String titleSelector;

    public static RagWebCrawlExtractConfig empty() {
        return new RagWebCrawlExtractConfig();
    }
}
