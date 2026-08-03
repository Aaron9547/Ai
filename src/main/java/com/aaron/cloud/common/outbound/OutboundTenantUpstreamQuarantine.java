package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 租户级短期隔离：在滑动窗口内多次出现凭证/欠费类硬错误后，一段时间内拦截该租户出站大模型相关调用，避免无效重试风暴。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundTenantUpstreamQuarantine {

    private static final String DEFAULT_USER_MESSAGE =
            "本租户模型访问在短时间内多次出现凭证失效、欠费或权限错误。"
                    + "为避免无效重试已暂时拦截大模型相关调用，请稍后重试或联系管理员检查 API Key 与计费账户。";

    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;

    private final ConcurrentHashMap<Long, TenantGate> gates = new ConcurrentHashMap<>();

    public void assertStreamAllowed(long tenantId) throws LlmOutboundException {
        if (tenantId <= 0) {
            return;
        }
        if (!effectiveEnabled(tenantId)) {
            return;
        }
        TenantGate g = gates.get(tenantId);
        if (g == null) {
            return;
        }
        synchronized (g) {
            long now = System.currentTimeMillis();
            if (now < g.quarantineUntilMs) {
                throw quarantineException(tenantId);
            }
        }
    }

    /** 任意出站路径成功时调用，解除该租户的失败累计与隔离。 */
    public void recordTenantSuccess(long tenantId) {
        if (tenantId <= 0) {
            return;
        }
        gates.remove(tenantId);
    }

    /**
     * 若 {@code ex} 为凭证/欠费类硬错误，则累计窗口内次数；达到阈值后进入隔离期。
     */
    public void recordHardFailureIfSignal(long tenantId, Throwable ex) {
        if (tenantId <= 0) {
            return;
        }
        if (!effectiveEnabled(tenantId)) {
            return;
        }
        if (!OutboundHardFailureSignals.isCredentialBillingOrQuota(ex)) {
            return;
        }
        var q = outboundResilienceRuntime.effective(tenantId).getTenantQuarantine();
        int windowMs = Math.max(10, q.getWindowSeconds()) * 1000;
        int threshold = Math.max(1, q.getThreshold());
        int cooldownMs = Math.max(5, q.getCooldownSeconds()) * 1000;

        TenantGate g = gates.computeIfAbsent(tenantId, k -> new TenantGate());
        synchronized (g) {
            long now = System.currentTimeMillis();
            if (now < g.quarantineUntilMs) {
                return;
            }
            if (g.windowStartMs == 0L || now - g.windowStartMs > windowMs) {
                g.windowStartMs = now;
                g.hardFailCount = 0;
            }
            g.hardFailCount++;
            log.warn(
                    "tenant upstream hard-failure signal tenantId={} streak={}/{} windowMs={}",
                    tenantId,
                    g.hardFailCount,
                    threshold,
                    windowMs);
            if (g.hardFailCount >= threshold) {
                g.quarantineUntilMs = now + cooldownMs;
                g.hardFailCount = 0;
                g.windowStartMs = 0L;
                log.warn(
                        "tenant upstream quarantine activated tenantId={} cooldownMs={} untilMs={}",
                        tenantId,
                        cooldownMs,
                        g.quarantineUntilMs);
            }
        }
    }

    private boolean effectiveEnabled(long tenantId) {
        return outboundResilienceRuntime.effective(tenantId).getTenantQuarantine().isEnabled();
    }

    private LlmOutboundException quarantineException(long tenantId) {
        String msg = outboundResilienceRuntime.effective(tenantId).getTenantQuarantine().getUserMessage();
        if (msg == null || msg.isBlank()) {
            msg = DEFAULT_USER_MESSAGE;
        } else {
            msg = msg.trim();
        }
        return new LlmOutboundException(
                OutboundKind.LLM_STREAM,
                "TENANT_UPSTREAM_QUARANTINE",
                msg,
                503,
                null,
                null,
                null,
                null);
    }

    private static final class TenantGate {
        long windowStartMs;
        int hardFailCount;
        long quarantineUntilMs;
    }
}
