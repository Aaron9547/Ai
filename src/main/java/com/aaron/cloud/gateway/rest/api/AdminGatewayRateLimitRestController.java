package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.gateway.entity.GwApiRateLimitRule;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayRateLimitApplicationService;
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
public class AdminGatewayRateLimitRestController extends ApiV1ControllerBases.AdminGatewayRateLimits {

    private final GatewayRateLimitApplicationService gatewayRateLimitApplicationService;

    @GetMapping
    public Object page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String rateScope,
            @RequestParam(required = false) Long tenantId) {
        TenantContextHolder.require();
        return gatewayRateLimitApplicationService.page(page, size, rateScope, tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GwApiRateLimitRule create(@RequestBody CreateRuleBody body) {
        return gatewayRateLimitApplicationService.create(
                body.getTenantId(),
                body.getPathPattern(),
                body.getHttpMethod(),
                body.getRequestsPerMinute() == null ? 120 : body.getRequestsPerMinute(),
                body.getEnabled(),
                body.getRemark());
    }

    @PutMapping("/{id}")
    public GwApiRateLimitRule update(@PathVariable long id, @RequestBody UpdateRuleBody body) {
        return gatewayRateLimitApplicationService.update(
                id,
                body.getTenantId(),
                body.getPathPattern(),
                body.getHttpMethod(),
                body.getRequestsPerMinute(),
                body.getEnabled(),
                body.getRemark());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        gatewayRateLimitApplicationService.delete(id);
    }

    @Data
    public static class CreateRuleBody {
        private Long tenantId;
        private String pathPattern;
        private String httpMethod;
        private Integer requestsPerMinute;
        private ToggleState enabled;
        private String remark;
    }

    @Data
    public static class UpdateRuleBody {
        private Long tenantId;
        private String pathPattern;
        private String httpMethod;
        private Integer requestsPerMinute;
        private ToggleState enabled;
        private String remark;
    }
}
