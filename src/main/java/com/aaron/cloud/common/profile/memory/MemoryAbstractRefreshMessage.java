package com.aaron.cloud.common.profile.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 异步刷新用户记忆抽象层（JSON）的队列消息体。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryAbstractRefreshMessage {

    private long tenantId;
    private String subjectKey;
    /** 用于抽象层 LLM 的语言模型 alias（与当前对话所选模型对齐）。 */
    private String modelAlias;
    /** 触发来源：user / assistant / conversation_create 等。 */
    private String trigger;
}
