package com.aaron.cloud.notification.message.admin;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;

final class MessageAdminEnumSupport {

    private MessageAdminEnumSupport() {}

    static MessageChannelStatus parseChannelStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return MessageChannelStatus.ACTIVE;
        }
        return "DISABLED".equalsIgnoreCase(raw.trim())
                ? MessageChannelStatus.DISABLED
                : MessageChannelStatus.ACTIVE;
    }

    static MessageTemplateStatus parseTemplateStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return MessageTemplateStatus.ACTIVE;
        }
        return "DISABLED".equalsIgnoreCase(raw.trim())
                ? MessageTemplateStatus.DISABLED
                : MessageTemplateStatus.ACTIVE;
    }

    static String channelStatusLabel(MessageChannelStatus status) {
        return status == MessageChannelStatus.DISABLED ? "DISABLED" : "ACTIVE";
    }

    static String templateStatusLabel(MessageTemplateStatus status) {
        return status == MessageTemplateStatus.DISABLED ? "DISABLED" : "ACTIVE";
    }

    static String deliveryStatusLabel(com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus status) {
        if (status == null) {
            return "QUEUED";
        }
        return switch (status) {
            case QUEUED -> "QUEUED";
            case SENDING -> "SENDING";
            case SUCCEEDED -> "SUCCEEDED";
            case FAILED -> "FAILED";
        };
    }
}
