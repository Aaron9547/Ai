package com.aaron.cloud.prompt.rest;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.prompt.admin.PromptTemplateAdminApplicationService;
import com.aaron.cloud.prompt.dto.PromptTemplateAdminDtos;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PromptTemplateAdminRestController extends ApiV1ControllerBases.AdminPromptTemplates {

    private final PromptTemplateAdminApplicationService adminService;

    @GetMapping
    public List<PromptTemplateAdminDtos.Row> list(
            @RequestParam(required = false) PromptTemplateDomain domain,
            @RequestParam(required = false) PromptTemplateKind promptKind,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Boolean enabled) {
        return adminService.list(domain, promptKind, locale, enabled);
    }

    @PostMapping
    public PromptTemplateAdminDtos.Row create(@Valid @RequestBody PromptTemplateAdminDtos.CreateBody body) {
        return adminService.create(body);
    }

    @PutMapping("/{id}")
    public PromptTemplateAdminDtos.Row update(
            @PathVariable long id, @Valid @RequestBody PromptTemplateAdminDtos.UpdateBody body) {
        return adminService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        adminService.delete(id);
    }

    @GetMapping("/cache-stats")
    public PromptTemplateAdminDtos.CacheStatsView cacheStats() {
        return adminService.cacheStats();
    }

    @PostMapping("/cache/evict")
    public void evictCache(@RequestBody(required = false) PromptTemplateAdminDtos.CacheEvictBody body) {
        adminService.evictCache(body);
    }
}
