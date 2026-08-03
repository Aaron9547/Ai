package com.aaron.cloud.common.api.ports;

import java.util.List;
import java.util.Map;

/** LLM 提示词模板解析；实现位于 {@code ai-prompt} 模块。 */
public interface PromptTemplateResolvePort {

    String resolveSystem(String promptCode, long tenantId, String locale);

    String resolveQuery(String promptCode, long tenantId);

    String resolveFragment(String promptCode, long tenantId, String locale);

    String renderUser(String promptCode, long tenantId, String locale, Map<String, String> variables);

    String renderSystem(String promptCode, long tenantId, String locale, Map<String, String> variables);

    String renderQuery(String promptCode, long tenantId, Map<String, String> variables);

    /** 按 {@code codePrefix_1}、{@code codePrefix_2} … 顺序收集 FRAGMENT，遇缺失即停。 */
    List<String> resolveFragmentSeries(String codePrefix, long tenantId, String locale);
}
