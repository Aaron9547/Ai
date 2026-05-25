package com.aaron.cloud.chat.intent.spi;

import com.aaron.cloud.chat.dto.ChatSendPayload;
import com.aaron.cloud.chat.intent.IntentKeywordMatchHit;
import com.aaron.cloud.chat.intent.flow.IntentMatchContext;
import com.aaron.cloud.common.api.dto.IntentHandlerConfigFieldMeta;
import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import com.aaron.cloud.common.api.intent.IntentHandlerParamSchemaBuilder;
import com.aaron.cloud.common.chat.entity.ChatAttachment;
import com.aaron.cloud.common.chat.entity.ChatIntentDefinition;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import com.aaron.cloud.common.context.TenantContextHolder;
import java.util.List;
import java.util.Optional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话意图处理器插件：关键词判定 + 专用 SSE；管理端扩展 JSON 由 {@link #handlerParamEnumClass()} 声明的<strong>参数枚举</strong>经
 * {@link #configSchema()} 转为动态表单（勿在前端写死字段表）。
 *
 * <p>新增处理器：实现本接口并注册为 Spring Bean，由 {@link IntentHandlerPluginRegistry} 收集；{@link com.aaron.cloud.chat.intent.ChatIntentStreamRouter}
 * 按 {@link ChatIntentHandlerKind} 查找，禁止在 Router 内写死分支。
 */
public interface ChatIntentHandlerPlugin {

    ChatIntentHandlerKind kind();

    Optional<IntentKeywordMatchHit> evaluateKeywordMatch(
            long conversationId,
            long tenantId,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            ChatSendPayload payload,
            List<ChatAttachment> attachments);

    /**
     * 带意图流上下文的匹配；默认回退到无上下文版本，便于旧处理器零改动接入。
     */
    default Optional<IntentKeywordMatchHit> evaluateKeywordMatch(
            long conversationId,
            long tenantId,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            IntentMatchContext flowContext) {
        return evaluateKeywordMatch(conversationId, tenantId, def, keywords, payload, attachments);
    }

    SseEmitter openStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit);

    /** 带流上下文；默认回退到无上下文版本。 */
    default SseEmitter openStream(
            long conversationId,
            TenantContextHolder.TenantSnapshot snap,
            ChatSendPayload payload,
            List<ChatAttachment> attachments,
            ChatIntentDefinition def,
            List<ChatIntentKeyword> keywords,
            IntentKeywordMatchHit matchHit,
            IntentMatchContext flowContext) {
        return openStream(conversationId, snap, payload, attachments, def, keywords, matchHit);
    }

    /**
     * 处理器参数枚举类（常量实现 {@link com.aaron.cloud.common.api.intent.IntentHandlerParamSpec}）；默认 {@code null} 表示无表单项。
     */
    @SuppressWarnings("rawtypes")
    default Class<? extends Enum> handlerParamEnumClass() {
        return null;
    }

    /** 由 {@link #handlerParamEnumClass()} 推导；一般无需覆盖 */
    default List<IntentHandlerConfigFieldMeta> configSchema() {
        return IntentHandlerParamSchemaBuilder.build(handlerParamEnumClass());
    }
}
