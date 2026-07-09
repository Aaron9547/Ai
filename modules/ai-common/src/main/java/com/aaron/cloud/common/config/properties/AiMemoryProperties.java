package com.aaron.cloud.common.config.properties;

import com.aaron.cloud.common.infra.AiInternalResourceNames;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户分层记忆：进程级 Milvus 开关；策略与队列轮询见 {@link com.aaron.cloud.common.platform.PlatformSettingApplicationService}。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class AiMemoryProperties {

    /** 为 true 且 {@code ai.providers.vector-store=milvus} 时写入用户记忆 Milvus collection。 */
    private boolean vectorEnabled = false;

    private String milvusCollection = AiInternalResourceNames.Milvus.USER_MEMORY_COLLECTION;
}
