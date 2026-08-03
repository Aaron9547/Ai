package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.enums.mcp.McpServerStatus;
import com.aaron.cloud.common.api.enums.mcp.McpTransportKind;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.mcp.McpServerRegistryRepository;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.mcp.builtin.BuiltinMcpServerNames;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.CreateMcpServerRequest;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.McpServerAdminView;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.UpdateMcpServerRequest;
import com.aaron.cloud.mcp.remote.McpRemoteClientFactory;
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
    private final McpRemoteClientFactory mcpRemoteClientFactory;

    public Page<McpServerAdminView> page(long pageNo, long pageSize) {
        long tenantId = TenantContextHolder.require().getTenantId();
        var src = mcpServerRegistryRepository.pageByTenant(tenantId, pageNo, pageSize);
        var dst = new Page<McpServerAdminView>(src.getCurrent(), src.getSize(), src.getTotal());
        dst.setRecords(src.getRecords().stream().map(this::toView).toList());
        return dst;
    }

    public McpServerAdminView create(CreateMcpServerRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        String name = req.getName().trim();
        rejectReservedServerName(name);
        String baseUrl = normalizeBaseUrl(req.getBaseUrl().trim());
        if (mcpServerRegistryRepository.countByTenantAndName(tenantId, name, null) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "mcp server name exists");
        }
        var row = new McpServerRegistry();
        row.setTenantId(tenantId);
        row.setName(name);
        row.setBaseUrl(baseUrl);
        row.setTransportKind(
                req.getTransportKind() == null ? McpTransportKind.STREAMABLE_HTTP : req.getTransportKind());
        row.setDescription(trimToNull(req.getDescription()));
        row.setAuthHeadersCipher(mcpRemoteClientFactory.encryptAuthHeadersFromApiKey(req.getApiKey()));
        row.setStatus(req.isEnabled() ? McpServerStatus.ACTIVE : McpServerStatus.DISABLED);
        mcpServerRegistryRepository.insert(row);
        return toView(requireRow(row.getId(), tenantId));
    }

    public McpServerAdminView update(long id, UpdateMcpServerRequest req) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        var row = requireRow(id, tenantId);
        if (req.getName() != null && !req.getName().isBlank()) {
            String name = req.getName().trim();
            rejectReservedServerName(name);
            if (mcpServerRegistryRepository.countByTenantAndName(tenantId, name, id) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "mcp server name exists");
            }
            row.setName(name);
        }
        if (req.getBaseUrl() != null && !req.getBaseUrl().isBlank()) {
            row.setBaseUrl(normalizeBaseUrl(req.getBaseUrl().trim()));
        }
        if (req.getTransportKind() != null) {
            row.setTransportKind(req.getTransportKind());
        }
        if (req.getDescription() != null) {
            row.setDescription(trimToNull(req.getDescription()));
        }
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            row.setAuthHeadersCipher(mcpRemoteClientFactory.encryptAuthHeadersFromApiKey(req.getApiKey()));
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

    private static void rejectReservedServerName(String name) {
        if (BuiltinMcpServerNames.PLATFORM.equalsIgnoreCase(name.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "server name 'platform' is reserved for builtin tools");
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl must start with http:// or https://");
        }
        return url;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private McpServerAdminView toView(McpServerRegistry r) {
        McpTransportKind tk = r.getTransportKind() == null ? McpTransportKind.STREAMABLE_HTTP : r.getTransportKind();
        return new McpServerAdminView(
                r.getId(),
                r.getTenantId(),
                r.getName(),
                r.getBaseUrl(),
                tk.name(),
                r.getDescription(),
                r.getAuthHeadersCipher() != null && !r.getAuthHeadersCipher().isBlank(),
                r.getStatus() == McpServerStatus.ACTIVE ? "ACTIVE" : "DISABLED",
                r.getLastProbeAt() != null ? ISO.format(r.getLastProbeAt()) : null,
                r.getLastProbeOk() == null ? null : r.getLastProbeOk() == 1,
                r.getCreatedAt() != null ? ISO.format(r.getCreatedAt()) : null,
                r.getUpdatedAt() != null ? ISO.format(r.getUpdatedAt()) : null);
    }
}
