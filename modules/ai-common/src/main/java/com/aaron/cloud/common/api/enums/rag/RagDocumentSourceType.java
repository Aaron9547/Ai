package com.aaron.cloud.common.api.enums.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** RAG 文档来源类型；持久化为 VARCHAR 码值。 */
@Getter
@RequiredArgsConstructor
public enum RagDocumentSourceType {
    FILE("FILE"),
    URL_CRAWL("URL_CRAWL"),
    FILE_UPLOAD("FILE_UPLOAD"),
    MANUAL("MANUAL");

    @EnumValue private final String code;
}
