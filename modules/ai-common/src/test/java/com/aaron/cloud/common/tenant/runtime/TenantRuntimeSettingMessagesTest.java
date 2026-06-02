package com.aaron.cloud.common.tenant.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.ReloadableUtf8ClasspathMessageSourceFactory;
import com.aaron.cloud.common.web.locale.AdminUiLocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

class TenantRuntimeSettingMessagesTest {

    private TenantRuntimeSettingMessages messages;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource ms =
                ReloadableUtf8ClasspathMessageSourceFactory.create(
                        TenantRuntimeMetaMessageSourceConfiguration.MESSAGE_BASENAME);
        messages = new TenantRuntimeSettingMessages(ms);
        LocaleContextHolder.setLocale(AdminUiLocaleResolver.CHINESE_ADMIN_UI);
    }

    @Test
    void fieldErrorChinese() {
        String text =
                messages.fieldError(
                        TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON,
                        TenantRuntimeSettingMessages.Validation.INVALID_JSON);
        assertEquals("内置固定源：JSON 格式不正确", text);
    }

    @Test
    void fieldErrorEnglish() {
        LocaleContextHolder.setLocale(AdminUiLocaleResolver.ENGLISH_ADMIN_UI);
        String text =
                messages.fieldError(
                        TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID,
                        TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_WEB_SEARCH);
        assertTrue(text.startsWith("Web model: "));
        assertTrue(text.contains("WEB_SEARCH"));
    }
}
