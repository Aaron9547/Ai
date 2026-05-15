package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboundResilienceConfiguration {

    /** 出站 LLM/嵌入/联网专用熔断注册表，与 Spring Cloud Gateway 用表隔离。 */
    @Bean(name = "aiOutboundCircuitBreakerRegistry")
    public CircuitBreakerRegistry aiOutboundCircuitBreakerRegistry(AiOutboundResilienceProperties props) {
        return CircuitBreakerRegistry.of(toCircuitBreakerConfig(props));
    }

    static CircuitBreakerConfig toCircuitBreakerConfig(AiOutboundResilienceProperties p) {
        var c = p.getCircuitBreaker();
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(c.getSlidingWindowSize())
                .minimumNumberOfCalls(c.getMinimumNumberOfCalls())
                .failureRateThreshold(c.getFailureRateThreshold())
                .waitDurationInOpenState(c.getWaitDurationInOpenState())
                .slowCallDurationThreshold(c.getSlowCallDurationThreshold())
                .slowCallRateThreshold(c.getSlowCallRateThreshold())
                .permittedNumberOfCallsInHalfOpenState(c.getPermittedNumberOfCallsInHalfOpenState())
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }
}
