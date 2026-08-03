package com.aaron.cloud.chat.websearch;

import java.util.List;

/** 前置联网检索聚合结果，注入主对话 system 上下文。 */
public record WebGroundingBundle(String summaryText, List<WebSearchReference> references) {}
