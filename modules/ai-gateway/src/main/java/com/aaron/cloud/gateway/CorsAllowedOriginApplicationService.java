package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.config.properties.AiCorsProperties;
import com.aaron.cloud.common.gateway.GwCorsAllowedOriginRepository;
import com.aaron.cloud.common.gateway.entity.GwCorsAllowedOrigin;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 解析对外 CORS 允许来源；首选库表启用行，其次 {@link AiCorsProperties#getFallbackWhenNoEnabledRows()}。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorsAllowedOriginApplicationService {

    /** 与 {@code gw_cors_allowed_origin.origin} VARCHAR(191) 及 utf8mb4 唯一索引上限一致。 */
    private static final int ORIGIN_MAX_LEN = 191;

    private final GwCorsAllowedOriginRepository repository;
    private final AiCorsProperties corsProperties;

    private volatile List<String> cachedEffectiveOrigins;

    public List<GwCorsAllowedOrigin> listAll() {
        return repository.listAllOrdered();
    }

    /** 供 Spring Security {@link org.springframework.web.cors.CorsConfigurationSource} 使用。 */
    public List<String> resolveEffectiveOriginsForRequest() {
        List<String> snap = cachedEffectiveOrigins;
        if (snap != null) {
            return snap;
        }
        synchronized (this) {
            if (cachedEffectiveOrigins == null) {
                cachedEffectiveOrigins = List.copyOf(computeEffectiveOrigins());
            }
            return cachedEffectiveOrigins;
        }
    }

    public void invalidateCache() {
        cachedEffectiveOrigins = null;
    }

    @Transactional
    public GwCorsAllowedOrigin create(String originRaw, ToggleState enabled, int sortOrder, String remark) {
        String norm = normalizeAndValidate(originRaw);
        if (repository.findByOriginExact(norm) != null) {
            throw new IllegalStateException("duplicate origin");
        }
        var row = new GwCorsAllowedOrigin();
        row.setOrigin(norm);
        row.setEnabled(enabled == null ? ToggleState.ON : enabled);
        row.setSortOrder(sortOrder);
        row.setRemark(remark);
        repository.insert(row);
        invalidateCache();
        return repository.findById(row.getId());
    }

    @Transactional
    public GwCorsAllowedOrigin update(long id, String originRaw, ToggleState enabled, Integer sortOrder, String remark) {
        GwCorsAllowedOrigin existing = repository.findById(id);
        if (existing == null) {
            throw new IllegalStateException("cors origin row not found");
        }
        String norm = normalizeAndValidate(originRaw);
        GwCorsAllowedOrigin other = repository.findByOriginExact(norm);
        if (other != null && !other.getId().equals(id)) {
            throw new IllegalStateException("duplicate origin");
        }
        existing.setOrigin(norm);
        if (enabled != null) {
            existing.setEnabled(enabled);
        }
        if (sortOrder != null) {
            existing.setSortOrder(sortOrder);
        }
        if (remark != null) {
            existing.setRemark(remark);
        }
        repository.updateById(existing);
        invalidateCache();
        return repository.findById(id);
    }

    @Transactional
    public void delete(long id) {
        repository.deleteById(id);
        invalidateCache();
    }

    /** 进程就绪后预热 CORS 缓存，避免首个浏览器请求才触发组装。 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        invalidateCache();
        resolveEffectiveOriginsForRequest();
    }

    private List<String> computeEffectiveOrigins() {
        List<GwCorsAllowedOrigin> enabledRows = repository.listEnabledOrdered();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (GwCorsAllowedOrigin row : enabledRows) {
            String o = normalizeOriginOnly(row.getOrigin());
            if (isValidHttpOrigin(o)) {
                set.add(o);
            } else {
                log.warn("skip invalid gw_cors_allowed_origin id={} origin={}", row.getId(), row.getOrigin());
            }
        }
        if (!set.isEmpty()) {
            return new ArrayList<>(set);
        }
        List<String> fb = corsProperties.getFallbackWhenNoEnabledRows();
        if (fb != null) {
            for (String raw : fb) {
                try {
                    String o = normalizeAndValidate(raw);
                    set.add(o);
                } catch (IllegalArgumentException ex) {
                    log.warn("skip invalid ai.cors.fallback-when-no-enabled-rows entry: {}", raw);
                }
            }
        }
        if (set.isEmpty()) {
            log.error(
                    "no CORS allowed origins: gw_cors_allowed_origin has no enabled rows and ai.cors.fallback-when-no-enabled-rows is empty — browser cross-origin requests will fail");
        }
        return new ArrayList<>(set);
    }

    private static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("origin required");
        }
        String n = normalizeOriginOnly(raw.trim());
        if (n.length() > ORIGIN_MAX_LEN) {
            throw new IllegalArgumentException("origin exceeds max length " + ORIGIN_MAX_LEN);
        }
        if (!isValidHttpOrigin(n)) {
            throw new IllegalArgumentException("origin must be http(s)://host[:port] without path");
        }
        return n;
    }

    /** 去掉尾部 `/`，压缩空白。 */
    private static String normalizeOriginOnly(String raw) {
        String s = raw.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean isValidHttpOrigin(String o) {
        try {
            URI u = URI.create(o);
            String scheme = u.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            if (u.getHost() == null || u.getHost().isBlank()) {
                return false;
            }
            String path = u.getPath();
            return path == null || path.isEmpty() || "/".equals(path);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
