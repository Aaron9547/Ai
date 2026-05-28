package com.aaron.cloud.common.metering;

/** 按 {@code ref_json.usageScene} 汇总的 token 用量（管理端场景看板）。 */
public record MeteringUsageBySceneView(String usageScene, long totalTokens, long eventCount) {}
