package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.context.LoginContextUtils;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayAccessPartyApplicationService;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantWizardApplicationService;
import com.aaron.cloud.gateway.GatewayAccessPartyApplicationService.CreateResult;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class AdminGwAccessPartyRestController extends ApiV1ControllerBases.AdminGatewayAccessParties {

    private final GatewayAccessPartyApplicationService accessPartyService;
    private final GatewayAccessPartyGrantWizardApplicationService grantWizardService;

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        long tenantId = LoginContextUtils.requireTenantId();
        return accessPartyService.page(tenantId, page, size);
    }

    @GetMapping("/{id}")
    public GwAccessParty get(@PathVariable long id) {
        return accessPartyService.get(LoginContextUtils.requireTenantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody UpsertBody body) {
        CreateResult r =
                accessPartyService.create(
                        LoginContextUtils.requireTenantId(),
                        body.getDisplayName(),
                        body.getAppId(),
                        body.getTotalRpmCap(),
                        body.getRemark(),
                        body.getStatus());
        return Map.of("party", r.party(), "plainSecret", r.plainSecret());
    }

    @PutMapping("/{id}")
    public GwAccessParty update(@PathVariable long id, @RequestBody UpsertBody body) {
        return accessPartyService.update(
                LoginContextUtils.requireTenantId(),
                id,
                body.getDisplayName(),
                body.getTotalRpmCap(),
                body.getRemark(),
                body.getStatus());
    }

    @PostMapping("/{id}/rotate-secret")
    public Map<String, String> rotateSecret(@PathVariable long id) {
        String secret = accessPartyService.rotateSecret(LoginContextUtils.requireTenantId(), id);
        return Map.of("plainSecret", secret);
    }

    @GetMapping("/{id}/grant-wizard")
    public GatewayAccessPartyGrantWizardApplicationService.WizardView grantWizard(@PathVariable long id) {
        return grantWizardService.load(LoginContextUtils.requireTenantId(), id);
    }

    @GetMapping("/{id}/integration-doc")
    public Map<String, String> integrationDoc(
            @PathVariable long id, @RequestParam(required = false) String baseUrl) {
        var doc = accessPartyService.buildIntegrationDoc(LoginContextUtils.requireTenantId(), id, baseUrl);
        return Map.of("filename", doc.filename(), "markdown", doc.markdown());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        accessPartyService.delete(LoginContextUtils.requireTenantId(), id);
    }

    @Data
    public static class UpsertBody {
        private String displayName;
        private String appId;
        private Integer totalRpmCap;
        private String remark;
        private ToggleState status;
    }
}
