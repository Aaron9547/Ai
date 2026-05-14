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

    private List<Long> attachmentIds;

    /**
     * 多轮意图流票据（服务端签发的不透明 flowId）；后续轮次用户消息可回传以绑定同一会话流。
     */
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "意图流票据仅允许字母数字")
    private String intentFlowTicket;

    /** 供编排与落库 meta 使用；与 {@link #webSearchEnabled} 字段分离，避免 Jackson 将 {@code isX} 误作独立属性。 */
    @JsonIgnore
    public boolean isWebSearchEnabled() {
        return Boolean.TRUE.equals(webSearchEnabled);
    }
}
