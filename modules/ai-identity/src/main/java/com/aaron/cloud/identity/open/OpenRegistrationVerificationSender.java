package com.aaron.cloud.identity.open;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.api.ports.MessageSendPort;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 按租户配置发送注册验证码邮件。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRegistrationVerificationSender {

    private final MessageSceneReadinessQuery sceneReadinessQuery;
    private final MessageSendPort messageSendPort;

    public void sendRegisterCode(
            long tenantId, String tenantName, String toEmail, String code, long ttlMinutes) {
        if (!sceneReadinessQuery.isSceneConfigured(tenantId, MessageSceneCode.REGISTER_VERIFICATION)) {
            throw new IllegalStateException("register_email_delivery_not_configured");
        }
        Map<String, String> vars =
                Map.of(
                        "code", code,
                        "ttlMinutes", String.valueOf(ttlMinutes),
                        "tenantName", tenantName == null ? "" : tenantName,
                        "email", toEmail);
        messageSendPort.send(
                MessageSendRequest.builder()
                        .tenantId(tenantId)
                        .sceneCode(MessageSceneCode.REGISTER_VERIFICATION)
                        .recipient(toEmail)
                        .templateVars(vars)
                        .idempotencyKey("reg:" + tenantId + ":" + toEmail + ":" + code)
                        .async(true)
                        .build());
        log.debug("注册验证码邮件已投递 target={}", toEmail);
    }
}
