package com.aaron.cloud.common.notifymeta;

import com.aaron.cloud.common.notifymeta.entity.NotificationWebhookSubscription;
import com.aaron.cloud.common.notifymeta.mapper.NotificationWebhookSubscriptionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationWebhookSubscriptionRepository {

    private final NotificationWebhookSubscriptionMapper mapper;

    public Page<NotificationWebhookSubscription> pageByTenant(
            long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<NotificationWebhookSubscription>lambdaQuery()
                        .eq(NotificationWebhookSubscription::getTenantId, tenantId)
                        .orderByDesc(NotificationWebhookSubscription::getUpdatedAt));
    }

    public int insert(NotificationWebhookSubscription row) {
        return mapper.insert(row);
    }
}
