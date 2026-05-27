package com.aaron.cloud.prompt;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import com.aaron.cloud.common.api.ports.PromptTemplateResolvePort;
import com.aaron.cloud.common.prompt.PromptTemplateRepository;
import com.aaron.cloud.common.prompt.entity.PromptTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptTemplateApplicationService implements PromptTemplateResolvePort {

    private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private final PromptTemplateRepository repository;
    private final ObjectProvider<PromptTemplateRedisCache> redisCache;

    @Override
    public String resolveSystem(String promptCode, long tenantId, String locale) {
        return resolveRaw(promptCode, tenantId, locale, PromptTemplateKind.SYSTEM);
    }

    @Override
    public String resolveQuery(String promptCode, long tenantId) {
        return resolveRaw(promptCode, tenantId, "*", PromptTemplateKind.QUERY);
    }

    @Override
    public String resolveFragment(String promptCode, long tenantId, String locale) {
        return resolveRaw(promptCode, tenantId, locale, PromptTemplateKind.FRAGMENT);
    }

    @Override
    public String renderUser(String promptCode, long tenantId, String locale, Map<String, String> variables) {
        String raw = resolveRaw(promptCode, tenantId, locale, PromptTemplateKind.USER);
        return renderVariables(raw, variables);
    }

    @Override
    public List<String> resolveFragmentSeries(String codePrefix, long tenantId, String locale) {
        String prefix = codePrefix == null ? "" : codePrefix.trim();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String code = prefix + "_" + i;
            String frag = resolveFragment(code, tenantId, locale);
            if (frag == null || frag.isBlank()) {
                break;
            }
            out.add(frag);
        }
        return List.copyOf(out);
    }

    public void evictCache(long tenantId, String promptCode, String locale) {
        PromptTemplateRedisCache cache = redisCache.getIfAvailable();
        if (cache != null) {
            cache.evict(tenantId, promptCode, locale);
            if (tenantId != 0L) {
                cache.evict(0L, promptCode, locale);
            }
        }
    }

    public void evictAllCache() {
        PromptTemplateRedisCache cache = redisCache.getIfAvailable();
        if (cache != null) {
            cache.evictAll();
        }
    }

    public boolean isResolveCacheAvailable() {
        return redisCache.getIfAvailable() != null;
    }

    public PromptTemplateRedisCache.CacheStats cacheStats() {
        PromptTemplateRedisCache cache = redisCache.getIfAvailable();
        return cache == null ? new PromptTemplateRedisCache.CacheStats(0, 0, 0) : cache.stats();
    }

    private String resolveRaw(String promptCode, long tenantId, String locale, PromptTemplateKind expectedKind) {
        String code = promptCode == null ? "" : promptCode.trim();
        if (code.isBlank()) {
            return "";
        }
        String loc = normalizeLocale(locale);
        PromptTemplateRedisCache cache = redisCache.getIfAvailable();
        if (cache != null) {
            String cached = cache.getOrNull(tenantId, code, loc);
            if (cached != null) {
                cache.recordHit();
                return cached;
            }
            cache.recordMiss();
        }
        String resolved = loadFromDbOrBuiltin(tenantId, code, loc, expectedKind);
        if (cache != null && resolved != null) {
            cache.put(tenantId, code, loc, resolved);
        }
        return resolved == null ? "" : resolved;
    }

    private String loadFromDbOrBuiltin(long tenantId, String code, String locale, PromptTemplateKind expectedKind) {
        Optional<PromptTemplate> row = repository.findActiveForResolve(tenantId, code, locale);
        if (row.isPresent()) {
            return row.get().getContent();
        }
        return PromptTemplateBuiltinCatalog.find(code, locale)
                .filter(e -> expectedKind == null || e.kind() == expectedKind)
                .map(PromptTemplateBuiltinCatalog.Entry::content)
                .orElse("");
    }

    static String renderVariables(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher m = VAR.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = variables.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh-CN";
        }
        return locale.trim();
    }
}
