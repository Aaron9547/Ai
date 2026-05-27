package com.aaron.cloud.common.message;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;
import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.common.message.entity.MsgTemplate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 仅基于库表判断场景是否已绑定可用通道（不发起外呼校验）。 */
@Component
@RequiredArgsConstructor
public class MessageSceneReadinessQuery {

    private final MsgTemplateRepository templateRepository;
    private final MsgChannelRepository channelRepository;

    public boolean isSceneConfigured(long tenantId, MessageSceneCode scene) {
        return resolveActiveRoute(tenantId, scene).isPresent();
    }

    public Optional<ResolvedRoute> resolveActiveRoute(long tenantId, MessageSceneCode scene) {
        Optional<MsgTemplate> templateOpt = templateRepository.findActiveByScene(tenantId, scene, "zh-CN");
        if (templateOpt.isEmpty() || templateOpt.get().getStatus() != MessageTemplateStatus.ACTIVE) {
            return Optional.empty();
        }
        MsgTemplate template = templateOpt.get();
        Optional<MsgChannel> channelOpt = channelRepository.findById(template.getChannelId(), tenantId);
        if (channelOpt.isEmpty() || channelOpt.get().getStatus() != MessageChannelStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedRoute(template, channelOpt.get()));
    }

    public record ResolvedRoute(MsgTemplate template, MsgChannel channel) {}
}
