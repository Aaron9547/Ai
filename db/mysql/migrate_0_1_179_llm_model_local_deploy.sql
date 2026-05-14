-- 0.1.179：llm_model 增加「是否本地部署」；向量模型为 1 时经 Feign 调 RAG 网关（路径变量为 sys_tenant.code），与对端 /privateModel/embedding 对齐。
ALTER TABLE llm_model
  ADD COLUMN local_deploy TINYINT NOT NULL DEFAULT 0 COMMENT '是否本地部署：1=向量嵌入经 ai.rag.local-embed-feign 转发；0=直连 openai_base_url' AFTER vector_backend;
