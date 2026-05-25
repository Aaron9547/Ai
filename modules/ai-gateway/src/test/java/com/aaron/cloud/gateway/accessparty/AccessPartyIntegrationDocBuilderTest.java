package com.aaron.cloud.gateway.accessparty;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService.GrantView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPartyIntegrationDocBuilderTest {

    @Test
    void buildContainsAppIdAndSigningHeaders() {
        var party = new GwAccessParty();
        party.setAppId("apdemo123");
        party.setDisplayName("Demo Party");
        party.setTotalRpmCap(120);
        party.setStatus(ToggleState.ON);

        var grant = new GwAccessPartyGrant();
        grant.setGrantedRpm(60);
        grant.setEnabled(ToggleState.ON);
        grant.setEndpointId(1L);

        var ep = new GwApiEndpoint();
        ep.setPathPattern("/partner/v1/health");
        ep.setHttpMethod("GET");
        ep.setDisplayName("Partner 健康检查");

        var doc =
                AccessPartyIntegrationDocBuilder.build(
                        party,
                        List.of(new GrantView(grant, ep, party)),
                        "https://api.example.com",
                        LocalDateTime.of(2026, 5, 25, 12, 0));

        assertTrue(doc.markdown().contains("apdemo123"));
        assertTrue(doc.markdown().contains("X-App-Id"));
        assertTrue(doc.markdown().contains("/partner/v1/health"));
        assertTrue(doc.filename().endsWith("-integration.md"));
    }
}
