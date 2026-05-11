package com.aaron.cloud.common.notifymeta.mapper;

import com.aaron.cloud.common.notifymeta.entity.NotificationWebhookSubscription;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationWebhookSubscriptionMapper
        extends BaseMapper<NotificationWebhookSubscription> {}
