package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.gateway.GwApiInterfaceKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.GwApiEndpointRepository;
import com.aaron.cloud.common.gateway.GwApiModuleRepository;
import com.aaron.cloud.common.gateway.LnkGwModuleEndpointRepository;
import com.aaron.cloud.common.gateway.entity.GwApiModule;
import com.aaron.cloud.common.gateway.entity.LnkGwModuleEndpoint;
import com.aaron.cloud.common.time.BeijingTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayApiModuleApplicationService {

    private final GwApiModuleRepository moduleRepository;
    private final LnkGwModuleEndpointRepository linkRepository;
    private final GwApiEndpointRepository endpointRepository;

    public List<GwApiModule> listAll() {
        return moduleRepository.listAllOrdered();
    }

    @Transactional
    public GwApiModule create(String code, String displayName, Integer sortOrder, ToggleState enabled, String remark) {
        String c = requireCode(code);
        if (moduleRepository.countByCode(c, null) > 0) {
            throw new IllegalArgumentException("duplicate module code");
        }
        var row = new GwApiModule();
        row.setCode(c);
        row.setDisplayName(requireText(displayName, "displayName required"));
        row.setSortOrder(sortOrder == null ? 0 : sortOrder);
        row.setEnabled(enabled == null ? ToggleState.ON : enabled);
        row.setRemark(remark);
        moduleRepository.insert(row);
        return Objects.requireNonNull(moduleRepository.findById(row.getId()));
    }

    @Transactional
    public GwApiModule update(
            long id, String displayName, Integer sortOrder, ToggleState enabled, String remark) {
        GwApiModule row = moduleRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("module not found");
        }
        if (displayName != null) {
            row.setDisplayName(requireText(displayName, "displayName required"));
        }
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        if (remark != null) {
            row.setRemark(remark);
        }
        moduleRepository.updateById(row);
        return Objects.requireNonNull(moduleRepository.findById(id));
    }

    @Transactional
    public void delete(long id) {
        GwApiModule row = moduleRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("module not found");
        }
        linkRepository.deleteByModuleId(id);
        moduleRepository.deleteById(id);
    }

    public List<LnkGwModuleEndpoint> listLinks(long moduleId) {
        return linkRepository.listByModuleId(moduleId);
    }

    @Transactional
    public void replaceModuleEndpoints(long moduleId, List<Long> endpointIds) {
        GwApiModule mod = moduleRepository.findById(moduleId);
        if (mod == null) {
            throw new IllegalArgumentException("module not found");
        }
        linkRepository.deleteByModuleId(moduleId);
        if (endpointIds == null) {
            return;
        }
        for (Long epId : endpointIds) {
            if (epId == null) {
                continue;
            }
            if (endpointRepository.findById(epId) == null) {
                throw new IllegalArgumentException("endpoint not found: " + epId);
            }
            var link = new LnkGwModuleEndpoint();
            link.setModuleId(moduleId);
            link.setEndpointId(epId);
            link.setCreatedAt(BeijingTime.nowLocal());
            linkRepository.insert(link);
            var ep = endpointRepository.findById(epId);
            ep.setModuleId(moduleId);
            if (ep.getInterfaceKind() == null) {
                ep.setInterfaceKind(mapModuleCodeToKind(mod.getCode()));
            }
            endpointRepository.updateById(ep);
        }
    }

    private static GwApiInterfaceKind mapModuleCodeToKind(String code) {
        if (code == null) {
            return GwApiInterfaceKind.OTHER;
        }
        return switch (code.trim().toUpperCase()) {
            case "MODEL" -> GwApiInterfaceKind.MODEL;
            case "ABILITY" -> GwApiInterfaceKind.ABILITY;
            case "KNOWLEDGE" -> GwApiInterfaceKind.KNOWLEDGE;
            default -> GwApiInterfaceKind.OTHER;
        };
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code required");
        }
        return code.trim().toUpperCase();
    }

    private static String requireText(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return v.trim();
    }
}
