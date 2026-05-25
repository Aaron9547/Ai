package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.enums.mcp.McpServerStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.mcp.McpServerRegistryRepository;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.CreateMcpServerRequest;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.McpServerAdminView;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.UpdateMcpServerRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class McpServerAdminApplicationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final McpServerRegistryRepository mcpServerRegistryRepository;

    public Page<McpServerAdminView> page(long pageNo, long pageSize) {
        long tenantId = TenantContextHolder.require().getTenantId();
        var src = mcpServerRegistryRepository.pageByTenant(tenantId, pageNo, pageSize);
        var dst = new Page<McpServerAdminView>(src.getCurrent(), src.getSize(), src.getTotal());
        dst.setRecords(src.getRecords().stream().map(this::toView).toList());
        return dst;
    }

    public McpServerAdminView create(CreateMcpServerRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        String name = req.getName().trim();
        String baseUrl = normalizeBaseUrl(req.getBaseUrl().trim());
        if (mcpServerRegistryRepository.countByTenantAndName(tenantId, name, null) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "mcp server name exists");
        }
        var row = new McpServerRegistry();
        row.setTenantId(tenantId);
        row.setName(name);
        row.setBaseUrl(baseUrl);
        row.setStatus(req.isEnabled() ? McpServerStatus.ACTIVE : McpServerStatus.DISABLED);
        mcpServerRegistryRepository.insert(row);
        return toView(requireRow(row.getId(), tenantId));
    }

    public McpServerAdminView update(long id, UpdateMcpServerRequest req) {
        long tenantId = TenantContextHolder.require().getTenantId();
        var row = requireRow(id, tenantId);
        if (req.getName() != null && !req.getName().isBlank()) {
            String name = req.getName().trim();
            if (mcpServerRegistryRepository.countByTenantAndName(tenantId, name, id) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "mcp server name exists");
            }
            row.setName(name);
        }
        if (req.getBaseUrl() != null && !req.getBaseUrl().isBlank()) {
            row.setBaseUrl(normalizeBaseUrl(req.getBaseUrl().trim()));
        }
        if (req.getEnabled() != null) {
            row.setStatus(req.getEnabled() ? McpServerStatus.ACTIVE : McpServerStatus.DISABLED);
        }
        mcpServerRegistryRepository.updateById(row);
        return toView(requireRow(id, tenantId));
    }

    public void delete(long id) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireRow(id, tenantId);
        mcpServerRegistryRepository.deleteByIdAndTenant(id, tenantId);
    }

    private McpServerRegistry requireRow(long id, long tenantId) {
        var row = mcpServerRegistryRepository.findByIdAndTenant(id, tenantId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mcp server not found");
        }
        return row;
    }

    private static String normalizeBaseUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl must start with http:// or https://");
        }
        return url;
    }

    private McpServerAdminView toView(McpServerRegistry r) {
        return new McpServerAdminView(
                r.getId(),
                r.getTenantId(),
                r.getName(),
                r.getBaseUrl(),
                r.getStatus() == McpServerStatus.ACTIVE ? "ACTIVE" : "DISABLED",
                r.getCreatedAt() != null ? ISO.format(r.getCreatedAt()) : null,
                r.getUpdatedAt() != null ? ISO.format(r.getUpdatedAt()) : null);
    }
}
