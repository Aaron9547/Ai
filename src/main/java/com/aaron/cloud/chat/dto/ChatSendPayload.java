package com.aaron.cloud.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

@Data
public class ChatSendPayload {

    @NotBlank
    @Size(max = 8000)
    private String content;

    @NotBlank private String modelAlias;

    /** 浠呭綋妯″瀷鏀寔鎬濊€冧笖鐢ㄦ埛寮€鍚椂锛孲SE 鍙兘涓嬪彂 reasoning 鍒嗙墖 */
    private boolean thinkingEnabled;

    /**
     * 是否在主模型前执行联网检索：仅当请求 JSON 显式为 {@code true} 时开启；缺省字段、{@code null}、{@code false} 均不检索。
     */
    @Getter(AccessLevel.NONE)
    @JsonDeserialize(using = WebSearchFlagDeserializer.class)
    private Boolean webSearchEnabled;

    @Getter(AccessLevel.NONE)
    @JsonDeserialize(using = WebSearchFlagDeserializer.class)
    private Boolean mcpEnabled;

    private List<Long> mcpServerIds;

    private List<Long> attachmentIds;

    /**
     * 客户端发送幂等键（建议 UUID）；同会话同键在短时窗内重复 POST 将被拒绝，避免连点/重试双写用户消息。
     */
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "clientSendKey 仅允许字母数字、下划线与连字符")
    private String clientSendKey;

    /**
     * 多轮意图流票据（服务端签发的不透明 flowId）；后续轮次用户消息可回传以绑定同一会话流。
     */
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "意图流票据仅允许字母数字")
    private String intentFlowTicket;

    /**
     * 用户端 UI 所选回复语种（与前端 i18n 一致）：{@code zh-CN} 或 {@code en-US}；注入 system 提示约束助手输出语言。
     */
    @Pattern(regexp = "^(zh-CN|en-US)?$", message = "responseLocale 仅支持 zh-CN 或 en-US")
    private String responseLocale;

    /** 供编排与落库 meta 使用；与 {@link #webSearchEnabled} 字段分离，避免 Jackson 将 {@code isX} 误作独立属性。 */
    @JsonIgnore
    public boolean isWebSearchEnabled() {
        return Boolean.TRUE.equals(webSearchEnabled);
    }

    @JsonIgnore
    public boolean isMcpEnabled() {
        return Boolean.TRUE.equals(mcpEnabled);
    }
}
