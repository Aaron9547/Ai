package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;

/** 消息发送中心统一入口；实现位于 {@code notification} 域（local）或 Feign remote 适配器。 */
public interface MessageSendPort {

    MessageSendResult send(MessageSendRequest request);
}
