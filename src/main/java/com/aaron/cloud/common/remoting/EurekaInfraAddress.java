package com.aaron.cloud.common.remoting;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EurekaInfraAddress {

    /**
     * 从 Eureka 取第一个实例的 HTTP(S) 基址（无尾斜杠），供 Milvus / MinIO 等客户端使用。
     */
    public static Optional<String> pickHttpBaseUri(DiscoveryClient client, String serviceId) {
        if (client == null || serviceId == null || serviceId.isBlank()) {
            return Optional.empty();
        }
        List<ServiceInstance> list = client.getInstances(serviceId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        ServiceInstance inst = list.getFirst();
        URI u = inst.getUri();
        if (u != null) {
            String s = u.toString();
            return Optional.of(s.endsWith("/") ? s.substring(0, s.length() - 1) : s);
        }
        String scheme = inst.isSecure() ? "https" : "http";
        return Optional.of(scheme + "://" + inst.getHost() + ":" + inst.getPort());
    }
}
