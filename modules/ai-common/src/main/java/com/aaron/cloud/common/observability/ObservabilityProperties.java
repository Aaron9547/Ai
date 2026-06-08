package com.aaron.cloud.common.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.observability")
public class ObservabilityProperties {

    /** 总开关：false 时 sink no-op */
    private boolean enabled = true;

    /** 自动链路事件保留天数 */
    private int retentionDays = 90;

    /** 分批 DELETE 行数 */
    private int purgeBatchSize = 5000;

    /** 异步队列容量（满则丢弃并 WARN） */
    private int queueCapacity = 1024;
}
