package com.aaron.cloud.rag.remote.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与对端 {@code PrivateModelService#callPrivateModel} 请求体一致：{@code model} + {@code input}（字符串数组）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocalEmbeddingRpcRequest {

    private String model;

    private List<String> input;
}
