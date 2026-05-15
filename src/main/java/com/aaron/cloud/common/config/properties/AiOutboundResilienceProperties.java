package com.aaron.cloud.common.config.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 前缀 {@code ai.outbound}；默认值见各字段。生产可用环境变量或 {@code -Dai.outbound.*} 覆盖（Spring 松散绑定，如
 * {@code AI_OUTBOUND_ENABLED}、{@code AI_OUTBOUND_TENANT_QUARANTINE_THRESHOLD}）。
 */
@Data
@ConfigurationProperties(prefix = "ai.outbound")
public class AiOutboundResilienceProperties {

    private boolean enabled = true;

    /** 流式：单线路最大重试次数（不含首次），仅零 token 时可重试。 */
    private int streamMaxAttempts = 3;

    /** 流式：初始退避毫秒。 */
    private long streamInitialBackoffMs = 250;

    /** 流式：最大退避毫秒。 */
    private long streamMaxBackoffMs = 8000;

    /** 同步调用（嵌入、联网 Bot）最大重试次数（不含首次）。 */
    private int syncMaxAttempts = 3;

    private long syncInitialBackoffMs = 200;

    private long syncMaxBackoffMs = 5000;

    /** 退避随机抖动比例 0~1。 */
    private double jitterRatio = 0.2;

    /** HttpClient 连接超时（秒）。 */
    private int connectTimeoutSeconds = 30;

    /** 流式整请求超时（秒）。 */
    private int streamRequestTimeoutSeconds = 300;

    /** 流式：首行 SSE 数据前最长等待（秒）。 */
    private int streamFirstLineTimeoutSeconds = 120;

    /** 流式：相邻两行之间最长空闲（秒）。 */
    private int streamLineIdleTimeoutSeconds = 120;

    private final CircuitBreakerProps circuitBreaker = new CircuitBreakerProps();

    /**
     * 租户级：凭证/欠费类错误在短时间重复出现时，进入隔离期并拒绝后续大模型相关出站（与按模型别名的熔断互补）。
     */
    @NestedConfigurationProperty
    private final TenantQuarantineProps tenantQuarantine = new TenantQuarantineProps();

    @Data
    public static class TenantQuarantineProps {
        private boolean enabled = true;
        /** 统计「硬错误」次数的滑动窗口长度（秒）。 */
        private int windowSeconds = 120;
        /** 窗口内达到此次数即触发隔离。 */
        private int threshold = 3;
        /** 触发隔离后，拒绝该租户出站调用的时长（秒）。 */
        private int cooldownSeconds = 180;
        /** 非空则覆盖默认中文说明（对话 SSE error 帧与用户可见文案）。 */
        private String userMessage = "";
    }

    @Data
    public static class CircuitBreakerProps {
        private boolean enabled = true;
        private int slidingWindowSize = 32;
        private int minimumNumberOfCalls = 8;
        private float failureRateThreshold = 50f;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        /** 流式调用耗时长，慢调用阈值设很大，主要依赖失败率。 */
        private Duration slowCallDurationThreshold = Duration.ofMinutes(15);
        private float slowCallRateThreshold = 100f;
        private int permittedNumberOfCallsInHalfOpenState = 3;
    }

    /** 可重试的 HTTP 状态码（流式首包前 / 同步）。 */
    private List<Integer> retryableHttpStatuses = defaultRetryableStatuses();

    private static List<Integer> defaultRetryableStatuses() {
        var l = new ArrayList<Integer>();
        l.add(408);
        l.add(425);
        l.add(429);
        l.add(500);
        l.add(502);
        l.add(503);
        l.add(504);
        return l;
    }

    public boolean isRetryableHttpStatus(int status) {
        return retryableHttpStatuses != null && retryableHttpStatuses.contains(status);
    }
}
