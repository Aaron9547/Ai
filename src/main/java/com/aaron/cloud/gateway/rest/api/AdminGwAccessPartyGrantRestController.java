package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.context.LoginContextUtils;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService.GrantUpsert;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService.GrantView;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminGwAccessPartyGrantRestController extends ApiV1ControllerBases.AdminGatewayAccessPartyGrants {

    private final GatewayAccessPartyGrantApplicationService grantService;

    @GetMapping
    public List<GrantView> list(@RequestParam long accessPartyId) {
        return grantService.listByAccessParty(LoginContextUtils.requireTenantId(), accessPartyId);
    }

    @PutMapping
    public List<GwAccessPartyGrant> replace(@RequestParam long accessPartyId, @RequestBody ReplaceBody body) {
        List<GrantUpsert> items =
                body.getItems() == null
                        ? List.of()
                        : body.getItems().stream()
                                .map(
                                        i ->
                                                new GrantUpsert(
                                                        i.getEndpointId(),
                                                        i.getModuleId(),
                                                        i.getGrantedRpm(),
                                                        i.getEnabled()))
                                .toList();
        return grantService.replaceGrants(LoginContextUtils.requireTenantId(), accessPartyId, items);
    }

    @PostMapping("/{grantId}/rpm")
    public GwAccessPartyGrant updateRpm(
            @RequestParam long accessPartyId, @PathVariable long grantId, @RequestBody RpmBody body) {
        return grantService.updateGrantRpm(
                LoginContextUtils.requireTenantId(),
                accessPartyId,
                grantId,
                body.getGrantedRpm() == null ? 0 : body.getGrantedRpm());
    }

    @Data
    public static class ReplaceBody {
        private List<GrantItem> items;
    }

    @Data
    public static class GrantItem {
        private Long endpointId;
        private Long moduleId;
        private Integer grantedRpm;
        private com.aaron.cloud.common.api.enums.gateway.ToggleState enabled;
    }

    @Data
    public static class RpmBody {
        private Integer grantedRpm;
    }
}
