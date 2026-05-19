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
    RAG_FILE_IMPORT("RAG_FILE_IMPORT"),
    /** 站点本地规则一条龙网页爬取（发现链接 + 批量入库）。 */
    RAG_SITE_CRAWL("RAG_SITE_CRAWL");

    @EnumValue private final String code;
}
