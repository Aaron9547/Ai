package com.aaron.cloud.model.config;

import com.aaron.cloud.common.config.ReloadableUtf8ClasspathMessageSourceFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理端 LLM 元数据文案专用 {@link MessageSource}（见 {@link ReloadableUtf8ClasspathMessageSourceFactory}）。
 */
@Configuration
public class LlmAdminMetaMessageSourceConfiguration {

    public static final String BEAN_NAME = "llmAdminMetaMessageSource";

    /** 与 {@code src/main/resources/llm-admin-meta_*.properties} 文件名前缀一致 */
    public static final String MESSAGE_BASENAME = "llm-admin-meta";

    @Bean(name = BEAN_NAME)
    MessageSource llmAdminMetaMessageSource() {
        return ReloadableUtf8ClasspathMessageSourceFactory.create(MESSAGE_BASENAME);
    }
}
