package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository;
import com.aaron.cloud.common.gateway.GwAccessPartyRepository;
import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayAccessPartyGrantApplicationService {

    private final GwAccessPartyRepository accessPartyRepository;
    private final GwAccessPartyGrantRepository grantRepository;
    private final GwApiEndpointRepository endpointRepository;

    public List<GrantView> listByAccessParty(long tenantId, long accessPartyId) {
        GwAccessParty party = requireParty(tenantId, accessPartyId);
        List<GwAccessPartyGrant> grants = grantRepository.listByAccessPartyId(accessPartyId);
        List<GrantView> views = new ArrayList<>();
        for (GwAccessPartyGrant g : grants) {
            GwApiEndpoint ep = endpointRepository.findById(g.getEndpointId());
            views.add(new GrantView(g, ep, party));
        }
        return views;
    }

    @Transactional
    public List<GwAccessPartyGrant> replaceGrants(long tenantId, long accessPartyId, List<GrantUpsert> items) {
        GwAccessParty party = requireParty(tenantId, accessPartyId);
        grantRepository.deleteByAccessPartyId(accessPartyId);
        int total = 0;
        List<GwAccessPartyGrant> saved = new ArrayList<>();
        if (items != null) {
            for (GrantUpsert item : items) {
                if (item.endpointId() == null) {
                    continue;
                }
                GwApiEndpoint ep = endpointRepository.findById(item.endpointId());
                if (ep == null) {
                    throw new IllegalArgumentException("endpoint not found");
                }
                int rpm = item.grantedRpm() == null ? 0 : Math.max(0, item.grantedRpm());
                validateEndpointPool(ep, rpm, null);
                total += rpm;
                var row = new GwAccessPartyGrant();
                row.setAccessPartyId(accessPartyId);
                row.setEndpointId(item.endpointId());
                row.setModuleId(item.moduleId());
                row.setGrantedRpm(rpm);
                row.setEnabled(item.enabled() == null ? ToggleState.ON : item.enabled());
                grantRepository.insert(row);
                saved.add(Objects.requireNonNull(grantRepository.findById(row.getId())));
            }
        }
        int cap = party.getTotalRpmCap() == null ? 0 : party.getTotalRpmCap();
        if (cap > 0 && total > cap) {
            throw new IllegalArgumentException("granted rpm sum exceeds access party total_rpm_cap");
        }
        return saved;
    }

    @Transactional
    public GwAccessPartyGrant updateGrantRpm(long tenantId, long accessPartyId, long grantId, int grantedRpm) {
        GwAccessParty party = requireParty(tenantId, accessPartyId);
        GwAccessPartyGrant grant = grantRepository.findById(grantId);
        if (grant == null || !grant.getAccessPartyId().equals(accessPartyId)) {
            throw new IllegalArgumentException("grant not found");
        }
        GwApiEndpoint ep = endpointRepository.findById(grant.getEndpointId());
        if (ep == null) {
            throw new IllegalArgumentException("endpoint not found");
        }
        int rpm = Math.max(0, grantedRpm);
        validateEndpointPool(ep, rpm, grantId);
        int others = grantRepository.sumGrantedRpmByAccessParty(accessPartyId, grantId);
        int cap = party.getTotalRpmCap() == null ? 0 : party.getTotalRpmCap();
        if (cap > 0 && others + rpm > cap) {
            throw new IllegalArgumentException("granted rpm sum exceeds access party total_rpm_cap");
        }
        grant.setGrantedRpm(rpm);
        grantRepository.updateById(grant);
        return Objects.requireNonNull(grantRepository.findById(grantId));
    }

    private void validateEndpointPool(GwApiEndpoint ep, int newRpm, Long excludeGrantId) {
        Integer global = ep.getGlobalRpmCap();
        if (global == null || global <= 0) {
            return;
        }
        int allocated = grantRepository.sumGrantedRpmByEndpoint(ep.getId(), excludeGrantId);
        if (allocated + newRpm > global) {
            throw new IllegalArgumentException("granted rpm exceeds endpoint global_rpm_cap");
        }
    }

    private GwAccessParty requireParty(long tenantId, long accessPartyId) {
        GwAccessParty party = accessPartyRepository.findById(accessPartyId);
        if (party == null || !party.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("access party not found");
        }
        return party;
    }

    public record GrantUpsert(Long endpointId, Long moduleId, Integer grantedRpm, ToggleState enabled) {}

    @Getter
    @RequiredArgsConstructor
    public static final class GrantView {
        private final GwAccessPartyGrant grant;
        private final GwApiEndpoint endpoint;
        private final GwAccessParty party;
    }
}
