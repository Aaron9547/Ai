package com.aaron.cloud.remoting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
@EnableFeignClients(basePackages = {"com.aaron.cloud.model.remote", "com.aaron.cloud.chat.remote", "com.aaron.cloud.identity.remote"})
public class RemotingConfiguration {}
