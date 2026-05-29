package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.knowledgeplanet.KnowledgeWeeklyPlan;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService;
import com.aaron.cloud.identity.knowledgeplanet.KnowledgePlanetWeeklyAdminTestApplicationService;
import com.aaron.cloud.identity.knowledgeplanet.KnowledgePlanetWeeklyAdminTestApplicationService.WeeklyTestPushResult;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService.MemoryEmbeddingModelSettingView;
import com.aaron.cloud.common.profile.admin.UserProfileAdminApplicationService.UserProfilePageResult;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：用户画像列表、详情、记忆向量化模型（落库 {@code ten_runtime_setting}）。 */
@RestController
@RequiredArgsConstructor
public class AdminUserProfileRestController extends ApiV1ControllerBases.AdminUserProfiles {

    private final UserProfileAdminApplicationService userProfileAdminApplicationService;
    private final KnowledgePlanetWeeklyAdminTestApplicationService weeklyAdminTestService;

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

    /** 知识星球周报测试：手动计算并可发信；{@code persist} 控制是否落库洞察与学习者画像。 */
    @PostMapping("/{userId}/knowledge-planet-weekly-test-push")
    public WeeklyTestPushView knowledgePlanetWeeklyTestPush(
            @PathVariable long userId,
            @RequestBody(required = false) KnowledgePlanetWeeklyTestPushBody body) {
        long tenantId = TenantContextHolder.require().getTenantId();
        boolean persist = body != null && Boolean.TRUE.equals(body.getPersist());
        boolean sendEmail = body == null || body.getSendEmail() == null || body.getSendEmail();
        LocalDate weekStart = null;
        if (body != null && body.getWeekStart() != null && !body.getWeekStart().isBlank()) {
            weekStart = LocalDate.parse(body.getWeekStart().trim());
        }
        WeeklyTestPushResult result =
                weeklyAdminTestService.trigger(tenantId, userId, persist, sendEmail, weekStart);
        return WeeklyTestPushView.from(result);
    }

    @Data
    public static class PutMemoryEmbeddingModelBody {
        /** null 表示清空，使用哈希占位向量 */
        private Long llmModelId;
    }

    @Data
    public static class KnowledgePlanetWeeklyTestPushBody {
        /** 是否写入 {@code ten_user_weekly_insight} 与学习者画像增量；默认 false */
        private Boolean persist;
        /** 是否发送周报邮件；默认 true */
        private Boolean sendEmail;
        /** 自然周周一，ISO-8601；空则当前周 */
        private String weekStart;
    }

    @Data
    public static class WeeklyTestPushView {
        private String computeStatus;
        private String computeMessage;
        private String weekStart;
        private boolean persisted;
        private String emailStatus;
        private String emailMessage;
        private String emailRecipient;
        private KnowledgeWeeklyPlan plan;

        static WeeklyTestPushView from(WeeklyTestPushResult result) {
            WeeklyTestPushView v = new WeeklyTestPushView();
            var c = result.compute();
            v.setComputeStatus(c.outcome().name());
            v.setComputeMessage(c.message());
            v.setWeekStart(c.weekStart() == null ? null : c.weekStart().toString());
            v.setPersisted(c.persisted());
            v.setPlan(c.plan());
            if (result.email() != null) {
                v.setEmailStatus(result.email().status());
                v.setEmailMessage(result.email().message());
                v.setEmailRecipient(result.email().recipient());
            }
            return v;
        }
    }
}
