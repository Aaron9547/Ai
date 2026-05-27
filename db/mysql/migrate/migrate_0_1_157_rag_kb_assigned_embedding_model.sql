-- 0.1.157：知识库绑定租户「向量模型」llm_model（VECTOR），Milvus 入库/检索按知识库解析嵌入端点。
ALTER TABLE rag_knowledge_base
  ADD COLUMN assigned_embedding_model_id BIGINT NULL
    COMMENT '绑定的嵌入模型 llm_model.id（须 model_kind=VECTOR；OpenAI 兼容 POST {base}/v1/embeddings）'
    AFTER assigned_llm_model_id;
