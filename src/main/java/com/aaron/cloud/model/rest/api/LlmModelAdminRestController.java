package com.aaron.cloud.model.rest.api;

import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.web.http.AdminUiNegotiationHeaders;
import com.aaron.cloud.common.web.locale.AdminUiLocaleResolver;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.model.LlmModelAdminApplicationService;
import com.aaron.cloud.model.LlmModelAdminUiMetaService;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.CreateLlmModelRequest;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.LlmModelAdminView;
import com.aaron.cloud.model.dto.LlmModelAdminDtos.UpdateLlmModelRequest;
import com.aaron.cloud.model.dto.LlmModelMetaDtos.LlmModelAdminMetaResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LlmModelAdminRestController extends ApiV1ControllerBases.LlmModels {

    private final LlmModelAdminApplicationService llmModelAdminApplicationService;
    private final LlmModelAdminUiMetaService llmModelAdminUiMetaService;

    /**
     * UI 元数据；语言由 {@link AdminUiLocaleResolver} 解析（{@code ?lang=} 优先于 {@code Accept-Language}）。
     */
    @GetMapping("/meta")
    public LlmModelAdminMetaResponse meta(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            Locale locale,
            HttpServletResponse response) {
        Locale effective = AdminUiLocaleResolver.resolve(lang, acceptLanguage, locale);
        String langRaw = AdminUiLocaleResolver.normalizedLangQueryRaw(lang);
        LlmModelAdminMetaResponse body = llmModelAdminUiMetaService.buildMeta(effective);
        AdminUiNegotiationHeaders.applyLlmModelMeta(response, effective, langRaw);
        String langTag = effective.toLanguageTag();
        return new LlmModelAdminMetaResponse(body.modelKindTabs(), body.optionLists(), langTag, langRaw);
    }

    @GetMapping
    public List<LlmModelAdminView> list(@RequestParam(required = false) String modelKind) {
        return llmModelAdminApplicationService.list(parseModelKind(modelKind));
    }

    private static LlmModelKind parseModelKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (LlmModelKind k : LlmModelKind.values()) {
            if (k.name().equalsIgnoreCase(s) || k.getCode().equalsIgnoreCase(s)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown modelKind: " + raw);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmModelAdminView create(@Valid @RequestBody CreateLlmModelRequest body) throws Exception {
        return llmModelAdminApplicationService.create(body);
    }

    @PutMapping("/{id}")
    public LlmModelAdminView update(@PathVariable long id, @Valid @RequestBody UpdateLlmModelRequest body)
            throws Exception {
        return llmModelAdminApplicationService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        llmModelAdminApplicationService.delete(id);
    }
}
