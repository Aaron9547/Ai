package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobTaskType {
    RAG_INDEX("RAG_INDEX"),
    RAG_URL_IMPORT("RAG_URL_IMPORT"),
    /** 文件入知识库：解析、分块、向量化（流水线占位，与 file 域对接后扩展）。 */
    RAG_FILE_IMPORT("RAG_FILE_IMPORT");

    @EnumValue private final String code;
}
