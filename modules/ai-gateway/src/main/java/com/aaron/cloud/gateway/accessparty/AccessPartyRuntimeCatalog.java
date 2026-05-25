package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository;
import com.aaron.cloud.common.gateway.GwAccessPartyRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessPartyRuntimeCatalog {

    private static final Logger log = LoggerFactory.getLogger(AccessPartyRuntimeCatalog.class);

    private final GwAccessPartyRepository accessPartyRepository;
    private final GwAccessPartyGrantRepository grantRepository;
    private final com.aaron.cloud.common.gateway.GwApiEndpointRepository endpointRepository;

    private volatile Map<Long, List<GwAccessPartyGrant>> grantsByPartyId = Map.of();
    private volatile List<GwApiEndpoint> enabledEndpoints = List.of();
    private volatile long loadedAtMillis;

    public GwAccessParty findActivePartyByAppId(String appId) {
        GwAccessParty party = accessPartyRepository.findByAppId(appId);
        if (party == null || party.getStatus() != ToggleState.ON) {
            return null;
        }
        return party;
    }

    public List<GwAccessPartyGrant> listEnabledGrants(long accessPartyId) {
        refreshIfNeeded();
        return grantsByPartyId.getOrDefault(accessPartyId, List.of());
    }

    public List<GwApiEndpoint> listEnabledEndpoints() {
        refreshIfNeeded();
        return enabledEndpoints;
    }

    public void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - loadedAtMillis < 30_000L && !enabledEndpoints.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (now - loadedAtMillis < 30_000L && !enabledEndpoints.isEmpty()) {
                return;
            }
            try {
                enabledEndpoints = endpointRepository.listAllEnabled();
                grantsByPartyId =
                        grantRepository.listAllEnabled().stream()
                                .collect(Collectors.groupingBy(GwAccessPartyGrant::getAccessPartyId));
                loadedAtMillis = System.currentTimeMillis();
            } catch (Exception ex) {
                log.error("failed to refresh access party catalog", ex);
                enabledEndpoints = List.of();
                grantsByPartyId = Map.of();
                loadedAtMillis = System.currentTimeMillis();
            }
        }
    }
}
