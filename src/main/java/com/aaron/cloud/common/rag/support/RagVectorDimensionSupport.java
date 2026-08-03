package com.aaron.cloud.common.rag.support;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 向量维数校验与解析（与 Milvus collection 维数对齐）。 */
public final class RagVectorDimensionSupport {

    /** 与百炼 text-embedding-v4 等常见可选维数对齐。 */
    public static final List<Integer> ALLOWED_VECTOR_DIMENSIONS =
            List.of(512, 768, 1024, 1536, 2048, 3072, 4096);

    private RagVectorDimensionSupport() {}

    public static Optional<Integer> parseDimensionOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            int n = Integer.parseInt(raw.trim());
            if (!ALLOWED_VECTOR_DIMENSIONS.contains(n)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "向量维数须为下列之一："
                                + ALLOWED_VECTOR_DIMENSIONS
                                + "（与嵌入模型、Milvus collection 一致）");
            }
            return Optional.of(n);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "向量维数须为正整数");
        }
    }
}
