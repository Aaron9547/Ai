package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.api.enums.gateway.GwApiInterfaceKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayApiEndpointApplicationService;
import com.aaron.cloud.gateway.GatewayApiEndpointOpenApiSyncApplicationService;
import java.util.List;
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
public class AdminGatewayApiEndpointRestController extends ApiV1ControllerBases.AdminGatewayApiEndpoints {

    private final GatewayApiEndpointApplicationService gatewayApiEndpointApplicationService;
    private final GatewayApiEndpointOpenApiSyncApplicationService openApiSyncApplicationService;

    /** 限流配置表单的快捷下拉数据（仅启用项）。 */
    @GetMapping("/picker")
    public List<GwApiEndpoint> picker() {
        TenantContextHolder.require();
        return gatewayApiEndpointApplicationService.listPicker();
    }

    @GetMapping
    public Object page(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        TenantContextHolder.require();
        return gatewayApiEndpointApplicationService.page(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GwApiEndpoint create(@RequestBody UpsertBody body) {
        return gatewayApiEndpointApplicationService.create(
                body.getPathPattern(),
                body.getHttpMethod(),
                body.getDisplayName(),
                body.getRemark(),
                body.getEnabled(),
                body.getSortOrder(),
                body.getModuleId(),
                body.getGlobalRpmCap(),
                body.getInterfaceKind(),
                body.getRequestSpecJson(),
                body.getResponseSpecJson());
    }

    @PutMapping("/{id}")
    public GwApiEndpoint update(@PathVariable long id, @RequestBody UpsertBody body) {
        return gatewayApiEndpointApplicationService.update(
                id,
                body.getPathPattern(),
                body.getHttpMethod(),
                body.getDisplayName(),
                body.getRemark(),
                body.getEnabled(),
                body.getSortOrder(),
                body.getModuleId(),
                body.getGlobalRpmCap(),
                body.getInterfaceKind(),
                body.getRequestSpecJson(),
                body.getResponseSpecJson());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        gatewayApiEndpointApplicationService.delete(id);
    }

    /** 从 SpringDoc OpenAPI 同步入参/出参 spec 到目录（默认仅补空字段）。 */
    @PostMapping("/sync-openapi-spec")
    public GatewayApiEndpointOpenApiSyncApplicationService.SyncResult syncOpenApiSpec(
            @RequestParam(defaultValue = "true") boolean emptyOnly) {
        TenantContextHolder.require();
        return openApiSyncApplicationService.syncAll(emptyOnly);
    }

    @PostMapping("/{id}/sync-openapi-spec")
    public GwApiEndpoint syncOpenApiSpecOne(
            @PathVariable long id, @RequestParam(defaultValue = "true") boolean emptyOnly) {
        TenantContextHolder.require();
        return openApiSyncApplicationService.syncOne(id, emptyOnly);
    }

    @Data
    public static class UpsertBody {
        private String pathPattern;
        private String httpMethod;
        private String displayName;
        private String remark;
        private ToggleState enabled;
        private Integer sortOrder;
        private Long moduleId;
        private Integer globalRpmCap;
        private GwApiInterfaceKind interfaceKind;
        private String requestSpecJson;
        private String responseSpecJson;
    }
}
