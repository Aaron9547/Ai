package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.accesslog.SysHttpAccessLogRepository;
import com.aaron.cloud.common.audit.SysAuditEventRepository;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.evalmeta.EvalPipelineRunRepository;
import com.aaron.cloud.common.filemeta.FileObjectMetaRepository;
import com.aaron.cloud.common.metering.MeteringUsageEventRepository;
import com.aaron.cloud.common.notifymeta.NotificationWebhookSubscriptionRepository;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminReadController extends ApiV1ControllerBases.AdminRead {

    private final SysHttpAccessLogRepository accessLogRepository;
    private final SysAuditEventRepository auditEventRepository;
    private final MeteringUsageEventRepository meteringUsageEventRepository;
    private final FileObjectMetaRepository fileObjectMetaRepository;
    private final NotificationWebhookSubscriptionRepository notificationWebhookSubscriptionRepository;
    private final EvalPipelineRunRepository evalPipelineRunRepository;

    @GetMapping("/access-logs")
    public Object accessLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        return accessLogRepository.pageForAdmin(tid, page, size);
    }

    @GetMapping("/audit-events")
    public Object audit(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        return auditEventRepository.pageForAdmin(tid, page, size);
    }

    @GetMapping("/metering-events")
    public Object metering(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        return meteringUsageEventRepository.pageForAdmin(tid, page, size);
    }

    @GetMapping("/file-objects")
    public Object files(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        var snap = TenantContextHolder.require();
        return fileObjectMetaRepository.pageByTenant(snap.getTenantId(), page, size);
    }

    @GetMapping("/notification-subscriptions")
    public Object notifications(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        var snap = TenantContextHolder.require();
        return notificationWebhookSubscriptionRepository.pageByTenant(snap.getTenantId(), page, size);
    }

    @GetMapping("/eval-runs")
    public Object evalRuns(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        var snap = TenantContextHolder.require();
        return evalPipelineRunRepository.pageByTenant(snap.getTenantId(), page, size);
    }
}
