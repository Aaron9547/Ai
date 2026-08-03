package com.aaron.cloud.scheduled.handler;

import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService;
import com.aaron.cloud.chat.knowledgeplanet.KnowledgePlanetWeeklyComputeService.WeeklyComputeResult;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.scheduled.TenantScheduledJobHandler;
import com.aaron.cloud.scheduled.run.TenantScheduledRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgePlanetWeeklyComputeJobHandler implements TenantScheduledJobHandler {

    private final KnowledgePlanetTenantRuntime planetRuntime;
    private final KnowledgePlanetWeeklyComputeService computeService;

    @Override
    public TenantScheduledExecutorCode executorCode() {
        return TenantScheduledExecutorCode.KNOWLEDGE_PLANET_WEEKLY_COMPUTE;
    }

    @Override
    public void execute(TenantScheduledTask registration, TenantScheduledRunContext runContext) throws Exception {
        long tenantId = registration.getTenantId();
        if (!planetRuntime.isEnabled(tenantId)) {
            runContext.report("SKIPPED", "租户未启用知识星球", 100, null, null);
            return;
        }
        runContext.report("COMPUTE", "正在计算本周成长方案", 20, null, null);
        WeeklyComputeResult result = computeService.computeForTenant(tenantId);
        if (result.skipReason() != null) {
            runContext.report("SKIPPED", result.skipReason(), 100, null, null);
            return;
        }
        runContext.report(
                "DONE",
                "完成：计算 "
                        + result.computed()
                        + "，跳过 "
                        + result.skipped()
                        + "，失败 "
                        + result.failed(),
                100,
                null,
                null);
        log.info(
                "[知识星球] 周一方案计算完成 tenantId={} computed={} skipped={} failed={}",
                tenantId,
                result.computed(),
                result.skipped(),
                result.failed());
    }
}
