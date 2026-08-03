package com.aaron.cloud.bootstrap;

import com.aaron.cloud.common.api.ports.RagQueryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 聚合部署校验：local 模式下 classpath 须存在 RAG 端口实现（{@link RagQueryPort}），防止误部署缺 rag 的全量包。
 */
@Component
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "local", matchIfMissing = true)
public class RagModuleDeploymentVerifier {

    private final ObjectProvider<RagQueryPort> ragQueryPort;

    public RagModuleDeploymentVerifier(ObjectProvider<RagQueryPort> ragQueryPort) {
        this.ragQueryPort = ragQueryPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyRagModulePresent() {
        if (ragQueryPort.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "ai.remoting.mode=local 但未装配 RagQueryPort；请确认 ai-rag 模块在 classpath 中，"
                            + "或切换 ai.remoting.mode=remote 并注册 ai-rag 服务");
        }
    }
}
