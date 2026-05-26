package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.scheduled.TenantScheduledTaskRepository;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shell 保存知识星球配置时，同步 {@code ten_scheduled_task} 注册行。 */
@Component
@RequiredArgsConstructor
public class KnowledgePlanetScheduledTaskSynchronizer {

    private final TenantScheduledTaskRepository scheduledTaskRepository;
    private final KnowledgePlanetTenantRuntime planetRuntime;

    public void syncForTenant(long tenantId) {
        boolean enabled = planetRuntime.isEnabled(tenantId);
        upsert(
                tenantId,
                TenantScheduledExecutorCode.KNOWLEDGE_PLANET_WEEKLY_COMPUTE,
                "知识星球·周一方案计算",
                planetRuntime.weeklyComputeCron(tenantId),
                enabled,
                true);
        upsert(
                tenantId,
                TenantScheduledExecutorCode.KNOWLEDGE_PLANET_WEEKLY_EMAIL,
                "知识星球·周一邮件推送",
                planetRuntime.weeklyEmailCron(tenantId),
                enabled,
                true);
    }

    private void upsert(
            long tenantId,
            TenantScheduledExecutorCode executor,
            String name,
            String cron,
            boolean enabled,
            boolean preserveCronOnUpdate) {
        List<TenantScheduledTask> existing = scheduledTaskRepository.listByTenant(tenantId, executor);
        if (existing.isEmpty()) {
            TenantScheduledTask row = new TenantScheduledTask();
            row.setTenantId(tenantId);
            row.setTaskType(executor.getCode());
            row.setName(name);
            row.setEnabled(enabled ? 1 : 0);
            row.setExecutorCode(executor);
            row.setCronExpression(cron);
            scheduledTaskRepository.insert(row);
            return;
        }
        TenantScheduledTask row = existing.getFirst();
        row.setName(name);
        row.setEnabled(enabled ? 1 : 0);
        if (!preserveCronOnUpdate || row.getCronExpression() == null || row.getCronExpression().isBlank()) {
            row.setCronExpression(cron);
        }
        scheduledTaskRepository.updateById(row);
    }
}
