package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.GwApiInterfaceKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayApiEndpointApplicationService {

    private final GwApiEndpointRepository endpointRepository;

    public Page<GwApiEndpoint> page(long pageNo, long pageSize) {
        return endpointRepository.pageAll(pageNo, pageSize);
    }

    public List<GwApiEndpoint> listPicker() {
        return endpointRepository.listPicker();
    }

    @Transactional
    public GwApiEndpoint create(
            String pathPattern,
            String httpMethod,
            String displayName,
            String remark,
            ToggleState enabled,
            Integer sortOrder,
            Long moduleId,
            Integer globalRpmCap,
            GwApiInterfaceKind interfaceKind,
            String requestSpecJson,
            String responseSpecJson) {
        String path = pathPattern == null ? "" : pathPattern.trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("pathPattern required");
        }
        String method = normalizeMethod(httpMethod);
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("displayName required");
        }
        if (endpointRepository.countByPathAndMethod(path, method, null) > 0) {
            throw new IllegalArgumentException("duplicate path_pattern and http_method");
        }
        var row = new GwApiEndpoint();
        row.setPathPattern(path);
        row.setHttpMethod(method);
        row.setDisplayName(name);
        row.setRemark(remark);
        row.setEnabled(enabled == null ? ToggleState.ON : enabled);
        row.setSortOrder(sortOrder == null ? 0 : sortOrder);
        row.setModuleId(moduleId);
        row.setGlobalRpmCap(globalRpmCap == null ? 0 : Math.max(0, globalRpmCap));
        row.setInterfaceKind(interfaceKind == null ? GwApiInterfaceKind.OTHER : interfaceKind);
        row.setRequestSpecJson(requestSpecJson);
        row.setResponseSpecJson(responseSpecJson);
        endpointRepository.insert(row);
        return Objects.requireNonNull(endpointRepository.findById(row.getId()));
    }

    @Transactional
    public GwApiEndpoint update(
            long id,
            String pathPattern,
            String httpMethod,
            String displayName,
            String remark,
            ToggleState enabled,
            Integer sortOrder,
            Long moduleId,
            Integer globalRpmCap,
            GwApiInterfaceKind interfaceKind,
            String requestSpecJson,
            String responseSpecJson) {
        GwApiEndpoint row = endpointRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("endpoint not found");
        }
        if (pathPattern != null) {
            String p = pathPattern.trim();
            if (p.isEmpty()) {
                throw new IllegalArgumentException("pathPattern required");
            }
            row.setPathPattern(p);
        }
        if (httpMethod != null) {
            row.setHttpMethod(normalizeMethod(httpMethod));
        }
        if (displayName != null) {
            String n = displayName.trim();
            if (n.isEmpty()) {
                throw new IllegalArgumentException("displayName required");
            }
            row.setDisplayName(n);
        }
        if (remark != null) {
            row.setRemark(remark);
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (moduleId != null) {
            row.setModuleId(moduleId);
        }
        if (globalRpmCap != null) {
            row.setGlobalRpmCap(Math.max(0, globalRpmCap));
        }
        if (interfaceKind != null) {
            row.setInterfaceKind(interfaceKind);
        }
        if (requestSpecJson != null) {
            row.setRequestSpecJson(requestSpecJson);
        }
        if (responseSpecJson != null) {
            row.setResponseSpecJson(responseSpecJson);
        }
        if (endpointRepository.countByPathAndMethod(row.getPathPattern(), row.getHttpMethod(), id) > 0) {
            throw new IllegalArgumentException("duplicate path_pattern and http_method");
        }
        endpointRepository.updateById(row);
        return Objects.requireNonNull(endpointRepository.findById(id));
    }

    @Transactional
    public void delete(long id) {
        GwApiEndpoint row = endpointRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("endpoint not found");
        }
        endpointRepository.deleteById(id);
    }

    private static String normalizeMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return "*";
        }
        return httpMethod.trim().toUpperCase();
    }
}
