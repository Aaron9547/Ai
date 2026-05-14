package com.aaron.cloud.common.tenant.runtime;

import java.util.List;

/**
 * 租户运行时 {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 与
 * {@link com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON} 解析结果。
 */
public record WebSearchGroundingMultiRoundConfig(int rounds, List<String> suffixes) {}
