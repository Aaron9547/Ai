package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.context.LoginContextUtils;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyCallLog;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayAccessPartyAuditApplicationService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminGwAccessPartyAuditRestController extends ApiV1ControllerBases.AdminGatewayAccessPartyAudit {

    private final GatewayAccessPartyAuditApplicationService auditService;

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "7") int days) {
        return auditService.summary(LoginContextUtils.requireTenantId(), days);
    }

    @GetMapping
    public Object page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long accessPartyId,
            @RequestParam(required = false) Long endpointId,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) LocalDateTime since) {
        return auditService.page(
                LoginContextUtils.requireTenantId(),
                accessPartyId,
                endpointId,
                httpStatus,
                since,
                page,
                size);
    }

    @GetMapping("/{id}")
    public GwAccessPartyCallLog detail(@PathVariable long id) {
        return auditService.get(LoginContextUtils.requireTenantId(), id);
    }
}
