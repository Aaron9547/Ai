package com.aaron.cloud.file;

import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.remoting.EurekaInfraAddress;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ai.providers.file-storage", havingValue = "minio")
public class MinioClientConfiguration {

    @Bean
    public MinioClient minioClient(
            AiProvidersProperties properties, ObjectProvider<DiscoveryClient> discoveryClient) {
        var m = properties.getMinio();
        String endpoint = m.getEndpoint();
        if (properties.isInfraViaDiscovery()) {
            var dc = discoveryClient.getIfAvailable();
            if (dc != null) {
                endpoint =
                        EurekaInfraAddress.pickHttpBaseUri(dc, m.getServiceId()).orElse(m.getEndpoint());
            }
        }
        var b = MinioClient.builder().endpoint(endpoint).credentials(m.getAccessKey(), m.getSecretKey());
        if (m.getRegion() != null && !m.getRegion().isBlank()) {
            b.region(m.getRegion());
        }
        return b.build();
    }
}
