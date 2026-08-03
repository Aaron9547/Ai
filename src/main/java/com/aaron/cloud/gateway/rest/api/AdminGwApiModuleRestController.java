package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwApiModule;
import com.aaron.cloud.common.gateway.entity.LnkGwModuleEndpoint;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.GatewayApiModuleApplicationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminGwApiModuleRestController extends ApiV1ControllerBases.AdminGatewayApiModules {

    private final GatewayApiModuleApplicationService moduleService;

    @GetMapping
    public List<GwApiModule> list() {
        return moduleService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GwApiModule create(@RequestBody UpsertBody body) {
        return moduleService.create(
                body.getCode(), body.getDisplayName(), body.getSortOrder(), body.getEnabled(), body.getRemark());
    }

    @PutMapping("/{id}")
    public GwApiModule update(@PathVariable long id, @RequestBody UpsertBody body) {
        return moduleService.update(id, body.getDisplayName(), body.getSortOrder(), body.getEnabled(), body.getRemark());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        moduleService.delete(id);
    }

    @GetMapping("/{id}/endpoints")
    public List<LnkGwModuleEndpoint> listLinks(@PathVariable long id) {
        return moduleService.listLinks(id);
    }

    @PutMapping("/{id}/endpoints")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceEndpoints(@PathVariable long id, @RequestBody EndpointsBody body) {
        moduleService.replaceModuleEndpoints(id, body == null ? null : body.getEndpointIds());
    }

    @Data
    public static class UpsertBody {
        private String code;
        private String displayName;
        private Integer sortOrder;
        private ToggleState enabled;
        private String remark;
    }

    @Data
    public static class EndpointsBody {
        private List<Long> endpointIds;
    }
}
