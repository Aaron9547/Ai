package com.aaron.cloud.remoting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
@EnableFeignClients(basePackages = "com.aaron.cloud.model.remote")
public class RemotingConfiguration {}
