package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;

/** 站点爬取运行时模板；实现位于 {@code rag} 域。 */
public interface SiteCrawlRuntimePort {

    String defaultRuntimeJsonForPreset(SiteCrawlPreset preset);
}
