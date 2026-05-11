package com.aaron.cloud.common.config.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 兜底：常态请在库表 {@code gw_cors_allowed_origin} 维护；仅当库中无任何「启用」行时使用下列列表（运维级救急）。
 */
@Data
@ConfigurationProperties(prefix = "ai.cors")
public class AiCorsProperties {

    /** 当库表无任何启用 Origin 时合并使用（可为空）。 */
    private List<String> fallbackWhenNoEnabledRows = new ArrayList<>();
}
