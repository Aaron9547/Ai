package com.aaron.cloud.notification;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.notifymeta.NotificationWebhookSubscriptionRepository;
import com.aaron.cloud.common.notifymeta.entity.NotificationWebhookSubscription;
import com.aaron.cloud.common.api.enums.notify.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final NotificationWebhookSubscriptionRepository subscriptionRepository;

    public long registerWebhook(String url, String secretHandle) {
        var snap = TenantContextHolder.require();
        var sub = new NotificationWebhookSubscription();
        sub.setTenantId(snap.getTenantId());
        sub.setTargetUrl(url);
        sub.setSecretHandle(secretHandle);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.insert(sub);
        return sub.getId();
    }
}
