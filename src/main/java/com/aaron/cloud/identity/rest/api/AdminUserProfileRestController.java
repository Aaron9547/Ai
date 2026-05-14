package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService.MemoryEmbeddingModelSettingView;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService.UserProfilePageResult;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：用户画像列表、详情、记忆向量化模型（落库 {@code ten_runtime_setting}）。 */
@RestController
@RequiredArgsConstructor
public class AdminUserProfileRestController extends ApiV1ControllerBases.AdminUserProfiles {

    private final UserProfileAdminApplicationService userProfileAdminApplicationService;

    @GetMapping
    public UserProfilePageResult list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        long tenantId = TenantContextHolder.require().getTenantId();
        return userProfileAdminApplicationService.listSummaries(tenantId, page, size, keyword);
    }

    /** 须声明在 {@code /{userId}} 之前，避免被当成数字路径变量。 */
    @GetMapping("/memory-embedding-model")
    public MemoryEmbeddingModelSettingView getMemoryEmbeddingModel() {
        long tenantId = TenantContextHolder.require().getTenantId();
        return userProfileAdminApplicationService.getMemoryEmbeddingSetting(tenantId);
    }

    @PutMapping("/memory-embedding-model")
    public void putMemoryEmbeddingModel(@RequestBody(required = false) PutMemoryEmbeddingModelBody body) {
        long tenantId = TenantContextHolder.require().getTenantId();
        userProfileAdminApplicationService.putMemoryEmbeddingModel(tenantId, body == null ? null : body.getLlmModelId());
    }

    @GetMapping("/{userId}")
    public JsonNode detail(@PathVariable long userId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        return userProfileAdminApplicationService.getUserProfileDetail(tenantId, userId);
    }

    @Data
    public static class PutMemoryEmbeddingModelBody {
        /** null 表示清空，使用哈希占位向量 */
        private Long llmModelId;
    }
}
