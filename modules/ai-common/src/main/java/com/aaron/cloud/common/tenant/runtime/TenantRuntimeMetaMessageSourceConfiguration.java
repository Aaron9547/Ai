package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.config.ReloadableUtf8ClasspathMessageSourceFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 租户运行参数校验与字段名双语文案（{@code tenant-runtime-meta_*.properties}）。 */
@Configuration
public class TenantRuntimeMetaMessageSourceConfiguration {

    public static final String BEAN_NAME = "tenantRuntimeMetaMessageSource";

    public static final String MESSAGE_BASENAME = "tenant-runtime-meta";

    @Bean(name = BEAN_NAME)
    MessageSource tenantRuntimeMetaMessageSource() {
        return ReloadableUtf8ClasspathMessageSourceFactory.create(MESSAGE_BASENAME);
    }
}
