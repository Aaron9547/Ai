package com.aaron.cloud.prompt.admin;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import com.aaron.cloud.common.prompt.PromptTemplateRepository;
import com.aaron.cloud.common.prompt.entity.PromptTemplate;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.prompt.PromptTemplateApplicationService;
import com.aaron.cloud.prompt.dto.PromptTemplateAdminDtos;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PromptTemplateAdminApplicationService {

    private final PromptTemplateRepository repository;
    private final PromptTemplateApplicationService applicationService;

    public List<PromptTemplateAdminDtos.Row> list(
            PromptTemplateDomain domain, PromptTemplateKind kind, String locale, Boolean enabled) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        return repository.listForAdminWorkspace(tid, domain, kind, locale, enabled).stream()
                .map(this::toRow)
                .toList();
    }

    public PromptTemplateAdminDtos.Row create(PromptTemplateAdminDtos.CreateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        String locale = body.locale() == null || body.locale().isBlank() ? "zh-CN" : body.locale().trim();
        int version =
                body.version() != null && body.version() > 0
                        ? body.version()
                        : repository.maxVersion(tid, body.promptCode().trim(), locale) + 1;
        PromptTemplate row = new PromptTemplate();
        row.setTenantId(tid);
        row.setPromptCode(body.promptCode().trim());
        row.setPromptKind(body.promptKind());
        row.setDomain(body.domain());
        row.setLocale(locale);
        row.setContent(body.content().trim());
        row.setVariablesSchemaJson(body.variablesSchemaJson());
        row.setVersion(version);
        row.setEnabled(body.enabled() == null || body.enabled());
        row.setRemark(body.remark());
        row.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        applicationService.evictCache(tid, row.getPromptCode(), locale);
        repository.insert(row);
        applicationService.evictCache(tid, row.getPromptCode(), locale);
        return toRow(repository.findByIdForAdmin(row.getId(), tid).orElse(row));
    }

    public PromptTemplateAdminDtos.Row update(long id, PromptTemplateAdminDtos.UpdateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        PromptTemplate row =
                repository
                        .findByIdForAdmin(id, tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        long rowTenantId = row.getTenantId();
        applicationService.evictCache(rowTenantId, row.getPromptCode(), row.getLocale());
        if (body.content() != null) {
            row.setContent(body.content().trim());
        }
        if (body.variablesSchemaJson() != null) {
            row.setVariablesSchemaJson(body.variablesSchemaJson());
        }
        if (body.enabled() != null) {
            row.setEnabled(body.enabled());
        }
        if (body.remark() != null) {
            row.setRemark(body.remark());
        }
        if (body.sortOrder() != null) {
            row.setSortOrder(body.sortOrder());
        }
        repository.updateByIdScoped(row);
        applicationService.evictCache(rowTenantId, row.getPromptCode(), row.getLocale());
        return toRow(repository.findByIdForAdmin(id, tid).orElse(row));
    }

    public void delete(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        PromptTemplate row =
                repository
                        .findByIdForAdmin(id, tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        long rowTenantId = row.getTenantId();
        applicationService.evictCache(rowTenantId, row.getPromptCode(), row.getLocale());
        repository.deleteByIdScoped(id, rowTenantId);
        applicationService.evictCache(rowTenantId, row.getPromptCode(), row.getLocale());
    }

    public PromptTemplateAdminDtos.CacheStatsView cacheStats() {
        boolean redisEnabled = applicationService.isResolveCacheAvailable();
        var s = applicationService.cacheStats();
        return new PromptTemplateAdminDtos.CacheStatsView(
                redisEnabled, s.approximateKeyCount(), s.hits(), s.misses());
    }

    public void evictCache(PromptTemplateAdminDtos.CacheEvictBody body) {
        if (body == null || (body.promptCode() == null || body.promptCode().isBlank())) {
            applicationService.evictAllCache();
            return;
        }
        long tid =
                body.tenantId() != null
                        ? body.tenantId()
                        : AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        String locale = body.locale() == null || body.locale().isBlank() ? "zh-CN" : body.locale().trim();
        applicationService.evictCache(tid, body.promptCode().trim(), locale);
    }

    private PromptTemplateAdminDtos.Row toRow(PromptTemplate row) {
        long rowTenantId = row.getTenantId() == null ? 0L : row.getTenantId();
        return new PromptTemplateAdminDtos.Row(
                row.getId(),
                rowTenantId,
                rowTenantId == 0L,
                row.getPromptCode(),
                row.getPromptKind(),
                row.getDomain(),
                row.getLocale(),
                row.getContent(),
                row.getVariablesSchemaJson(),
                row.getVersion() == null ? 1 : row.getVersion(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getRemark(),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                row.getUpdatedAt());
    }
}
