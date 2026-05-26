package com.aaron.cloud.common.api.enums.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 混合检索试跑/诊断：单条命中来自 Milvus 向量或 Elasticsearch 关键词分支。 */
@Getter
@RequiredArgsConstructor
public enum RagRetrievalHitSource {
    MILVUS("milvus"),
    ES("es");

    @EnumValue
    @JsonValue
    private final String code;
}
