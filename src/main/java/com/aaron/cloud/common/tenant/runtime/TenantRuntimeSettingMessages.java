package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.web.locale.AdminUiLocaleResolver;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * 租户运行参数校验文案：按 {@code Accept-Language} / {@link AdminUiLocaleResolver} 解析 {@code tenant-runtime-meta_*.properties}。
 */
@Component
public class TenantRuntimeSettingMessages {

    public static final String PREFIX = "tenant.runtime.";

    /** {@link #badField} 明细码 */
    public static final class Validation {
        public static final String REQUIRED = "validation.required";
        public static final String BOOLEAN_VALUE = "validation.booleanValue";
        public static final String MODEL_ID_OR_EMPTY = "validation.modelIdOrEmpty";
        public static final String MUST_BE_JSON_ARRAY = "validation.mustBeJsonArray";
        public static final String FIXED_SOURCE_ELEMENT_STRING = "validation.fixedSourceElementString";
        public static final String UNKNOWN_FIXED_SOURCE = "validation.unknownFixedSource";
        public static final String AT_LEAST_ONE_VALID_FIXED_SOURCE = "validation.atLeastOneValidFixedSource";
        public static final String INVALID_JSON = "validation.invalidJson";
        public static final String ROUND_COUNT_RANGE = "validation.roundCountRange";
        public static final String TOO_LONG = "validation.tooLong";
        public static final String ARRAY_ELEMENT_PRIMITIVE = "validation.arrayElementPrimitive";
        public static final String RETRIEVAL_MODE = "validation.retrievalMode";
        public static final String MUST_BE_JSON_OBJECT = "validation.mustBeJsonObject";
        public static final String PROXY_HOST_REQUIRED = "validation.proxyHostRequired";
        public static final String PORT_RANGE = "validation.portRange";
        public static final String PROXY_TYPE_HTTP_SOCKS = "validation.proxyTypeHttpSocks";
        public static final String MODEL_ID_INVALID = "validation.modelIdInvalid";
        public static final String MODEL_NOT_FOUND_IN_TENANT = "validation.modelNotFoundInTenant";
        public static final String MODEL_MUST_BE_ACTIVE = "validation.modelMustBeActive";
        public static final String MODEL_MUST_BE_WEB_SEARCH = "validation.modelMustBeWebSearch";
        public static final String MODEL_MUST_BE_LANGUAGE_OR_WEB_SEARCH = "validation.modelMustBeLanguageOrWebSearch";
        public static final String MODEL_MUST_BE_LANGUAGE_CHAT = "validation.modelMustBeLanguageChat";

        private Validation() {}
    }

    /** Shell 专页（无字段前缀） */
    public static final class Shell {
        public static final String WEB_SEARCH_GROUNDING_REQUIRED = "shell.webSearchGroundingRequired";
        public static final String JSON_FIELDS_TOO_LONG = "shell.jsonFieldsTooLong";
        public static final String OUTBOUND_JSON_TOO_LONG = "shell.outboundJsonTooLong";

        private Shell() {}
    }

    private static final Locale FALLBACK = AdminUiLocaleResolver.CHINESE_ADMIN_UI;

    private final MessageSource messageSource;

    TenantRuntimeSettingMessages(
            @Qualifier(TenantRuntimeMetaMessageSourceConfiguration.BEAN_NAME) MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Locale requestLocale() {
        Locale spring = LocaleContextHolder.getLocale();
        return AdminUiLocaleResolver.resolve(null, acceptLanguageHeader(), spring);
    }

    public String fieldLabel(TenantRuntimeSettingKey key, Locale locale) {
        String code = PREFIX + "field." + key.getStorage();
        Locale effective = locale != null ? locale : FALLBACK;
        try {
            return messageSource.getMessage(code, null, effective);
        } catch (NoSuchMessageException ex) {
            try {
                return messageSource.getMessage(code, null, FALLBACK);
            } catch (NoSuchMessageException ex2) {
                return key.getDescriptionZh();
            }
        }
    }

    public String fieldLabel(TenantRuntimeSettingKey key) {
        return fieldLabel(key, requestLocale());
    }

    public String fieldError(TenantRuntimeSettingKey key, String validationCode, Object... args) {
        Locale locale = requestLocale();
        return fieldLabel(key, locale) + separator(locale) + msg(PREFIX + validationCode, locale, args);
    }

    public ResponseStatusException badField(TenantRuntimeSettingKey key, String validationCode, Object... args) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldError(key, validationCode, args));
    }

    public ResponseStatusException badRequest(String code, Object... args) {
        Locale locale = requestLocale();
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg(PREFIX + code, locale, args));
    }

    private static String separator(Locale locale) {
        return AdminUiLocaleResolver.CHINESE_ADMIN_UI.equals(locale) ? "：" : ": ";
    }

    @Nullable
    private static String acceptLanguageHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getHeader("Accept-Language");
        }
        return null;
    }

    private String msg(String code, Locale locale, @Nullable Object[] args) {
        Locale effective = locale != null ? locale : FALLBACK;
        try {
            return messageSource.getMessage(code, args, effective);
        } catch (NoSuchMessageException ex) {
            try {
                return messageSource.getMessage(code, args, FALLBACK);
            } catch (NoSuchMessageException ex2) {
                return code;
            }
        }
    }
}
