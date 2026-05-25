package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.GwApiModuleRepository;
import com.aaron.cloud.common.gateway.LnkGwModuleEndpointRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.gateway.entity.GwApiModule;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService.GrantView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatewayAccessPartyGrantWizardApplicationService {

    private final GatewayAccessPartyApplicationService accessPartyService;
    private final GatewayAccessPartyGrantApplicationService grantService;
    private final GwApiModuleRepository moduleRepository;
    private final LnkGwModuleEndpointRepository linkRepository;
    private final GwApiEndpointRepository endpointRepository;

    public record ModuleStep(GwApiModule module, List<GwApiEndpoint> endpoints) {}

    public record WizardView(GwAccessParty party, List<ModuleStep> modules, List<GrantView> grants) {}

    public WizardView load(long tenantId, long partyId) {
        GwAccessParty party = accessPartyService.get(tenantId, partyId);
        List<GwApiModule> modules = moduleRepository.listAllOrdered().stream()
                .filter(m -> m.getEnabled() == ToggleState.ON)
                .toList();
        List<ModuleStep> steps = new ArrayList<>();
        for (GwApiModule mod : modules) {
            List<Long> epIds =
                    linkRepository.listByModuleId(mod.getId()).stream()
                            .map(l -> l.getEndpointId())
                            .toList();
            List<GwApiEndpoint> endpoints = new ArrayList<>();
            for (Long epId : epIds) {
                GwApiEndpoint ep = endpointRepository.findById(epId);
                if (ep != null && ep.getEnabled() == ToggleState.ON) {
                    endpoints.add(ep);
                }
            }
            steps.add(new ModuleStep(mod, endpoints));
        }
        List<GrantView> grants = grantService.listByAccessParty(tenantId, partyId);
        return new WizardView(party, steps, grants);
    }
}
