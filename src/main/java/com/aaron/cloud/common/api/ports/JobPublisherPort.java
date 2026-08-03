package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.job.JobDispatchMessage;

/** 异步任务 MQ 发布；实现位于 {@code job} 域（RocketMQ 启用时装配）。 */
public interface JobPublisherPort {

    void publish(JobDispatchMessage message);
}
