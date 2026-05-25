package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Milvus 路径下：知识库须绑定本租户已启用的 {@link LlmModelKind#VECTOR} 模型（llm_model），否则拒绝爬取/入库等写向量操作。
 *
 * <p>嵌入调用为 OpenAI 兼容 JSON；URL 由 {@code llm_model.integration_backend} 在 rag 嵌入服务中解析。内网免鉴权可不配置 API Key。
 */
@Component
@RequiredArgsConstructor
public class RagKbVectorModelGuard {

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final SysLlmModelRepository sysLlmModelRepository;

    /** 校验知识库已绑定可用的向量模型；未绑定或模型非法时抛出 400。 */
    public void assertKbHasVectorEmbeddingModel(long tenantId, long kbId) {
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        if (kb == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        Long mid = kb.getAssignedEmbeddingModelId();
        if (mid == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请先在知识库高级设置中绑定「向量模型」（嵌入），再执行爬取或向量化入库。");
        }
        SysLlmModel m =
                sysLlmModelRepository
                        .findById(tenantId, mid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "知识库绑定的向量模型不存在或不属于当前租户"));
        LlmModelKind k = m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.VECTOR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "知识库嵌入须绑定类型为「向量」的模型配置（llm_model.model_kind=VECTOR）");
        }
        if (m.getStatus() != LlmModelStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库绑定的向量模型未启用");
        }
        if (m.getOpenaiBaseUrl() == null || m.getOpenaiBaseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库绑定的向量模型未配置 Base URL");
        }
        if (m.getOpenaiModelId() == null || m.getOpenaiModelId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库绑定的向量模型未配置厂商模型 ID");
        }
    }
}
