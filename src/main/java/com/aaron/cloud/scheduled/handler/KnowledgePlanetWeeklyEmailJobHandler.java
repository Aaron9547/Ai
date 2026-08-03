package com.aaron.cloud.scheduled.handler;

import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.identity.knowledgeplanet.KnowledgePlanetWeeklyEmailApplicationService;
import com.aaron.cloud.identity.knowledgeplanet.KnowledgePlanetWeeklyEmailApplicationService.WeeklyEmailResult;
import com.aaron.cloud.scheduled.TenantScheduledJobHandler;
import com.aaron.cloud.scheduled.run.TenantScheduledRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyEmailJobHandler implements TenantScheduledJobHandler {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final KnowledgePlanetWeeklyEmailApplicationService emailService;

    @Override
    public TenantScheduledExecutorCode executorCode() {
        return TenantScheduledExecutorCode.KNOWLEDGE_PLANET_WEEKLY_EMAIL;
    }

    @Override
    public void execute(TenantScheduledTask registration, TenantScheduledRunContext runContext) throws Exception {
        long tenantId = registration.getTenantId();
        if (!planetRuntime.isEnabled(tenantId)) {
            runContext.report("SKIPPED", "租户未启用知识星球", 100, null, null);
            return;
        }
        runContext.report("EMAIL", "正在发送周报邮件", 30, null, null);
        WeeklyEmailResult result =
                emailService.sendForTenant(
                        tenantId, KnowledgePlanetWeeklyComputeService.mondayOfCurrentWeek());
        if (result.skipReason() != null) {
            runContext.report("SKIPPED", result.skipReason(), 100, null, null);
            return;
        }
        runContext.report(
                "DONE",
                "完成：发送 " + result.sent() + "，跳过 " + result.skipped() + "，失败 " + result.failed(),
                100,
                null,
                null);
        log.info(
                "[知识星球] 周一邮件推送完成 tenantId={} sent={} skipped={} failed={}",
                tenantId,
                result.sent(),
                result.skipped(),
                result.failed());
    }
}
