package com.aaron.cloud.chat.dto;

import lombok.Data;

/**
 * 重新生成助手回复时的可选覆盖（与 {@link ChatSendPayload} 语义对齐）；未传字段则沿用该轮用户消息 meta 中记录的模型与思考开关。
 */
@Data
public class ChatRegenerateRequest {

    /** 覆盖为当前 UI 所选模型别名；空白或未传则仍用用户消息 meta */
    private String modelAlias;

    /** 覆盖是否开启思考流；未传则仍用用户消息 meta */
    private Boolean thinkingEnabled;

    /** 覆盖是否开启联网检索；未传则仍用用户消息 meta */
    private Boolean webSearchEnabled;
}
