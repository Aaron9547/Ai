package com.aaron.cloud.common.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * 构造 UTF-8 {@link ReloadableResourceBundleMessageSource}，并关闭「回退到 JVM 系统语言」，避免服务器默认 {@code en_US} 时与显式 {@code zh_CN} 请求不一致。
 *
 * <p>各业务模块注册专用 {@link org.springframework.context.MessageSource} Bean 时调用 {@link #create(String)}，{@code basename} 不含 {@code .properties} 与语言后缀，例如 {@code "llm-admin-meta"}。
 */
public final class ReloadableUtf8ClasspathMessageSourceFactory {

    private ReloadableUtf8ClasspathMessageSourceFactory() {}

    /**
     * @param basenameWithoutPrefix classpath 下资源前缀，不含 {@code .properties}，例如 {@code llm-admin-meta}（将加载 {@code classpath:llm-admin-meta_zh_CN.properties} 等）
     */
    public static ReloadableResourceBundleMessageSource create(String basenameWithoutPrefix) {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:" + basenameWithoutPrefix);
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(false);
        ms.setCacheSeconds(3600);
        return ms;
    }
}
