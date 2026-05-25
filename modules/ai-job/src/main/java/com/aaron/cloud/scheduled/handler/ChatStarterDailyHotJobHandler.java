package com.aaron.cloud.scheduled.handler;

import com.aaron.cloud.chat.starter.ChatStarterDailyHotTopicService;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import com.aaron.cloud.scheduled.TenantScheduledJobHandler;
import com.aaron.cloud.scheduled.run.TenantScheduledRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 对话推荐问题：每日联网热点兜底池刷新（由 {@code ten_scheduled_task} 调度）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStarterDailyHotJobHandler implements TenantScheduledJobHandler {

    private final ChatStarterDailyHotTopicService dailyHotTopicService;
    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @Override
    public TenantScheduledExecutorCode executorCode() {
        return TenantScheduledExecutorCode.CHAT_STARTER_DAILY_HOT;
    }

    @Override
    public void execute(TenantScheduledTask registration, TenantScheduledRunContext runContext)
            throws Exception {
        long tenantId = registration.getTenantId();
        runContext.report("CHECK", "检查联网搜索模型配置", 5, null, null);
        if (llmModelRepository
                .resolveWebSearchModel(
                        tenantId, tenantRuntimeSettingApplicationService.webSearchGroundingModelId(tenantId))
                .isEmpty()) {
            log.warn(
                    "[推荐问题] 每日热点跳过：租户未配置可用的联网搜索模型 tenantId={} taskId={}",
                    tenantId,
                    registration.getId());
            runContext.report("SKIPPED", "未配置可用的联网搜索模型", 100, null, null);
            return;
        }
        runContext.report("FETCH", "正在联网抓取并生成热点问句", 20, null, null);
        dailyHotTopicService.refreshForTenant(tenantId, false);
        runContext.report("PERSIST", "热点问句已写入推荐池", 90, null, null);
        log.info(
                "[推荐问题] 每日热点定时执行完成 tenantId={} taskId={}",
                tenantId,
                registration.getId());
    }
}
