package com.aaron.cloud.common.mcp;

import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.common.mcp.mapper.McpServerRegistryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class McpServerRegistryRepository {

    private final McpServerRegistryMapper mapper;

    public Page<McpServerRegistry> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getTenantId, tenantId)
                        .orderByDesc(McpServerRegistry::getUpdatedAt));
    }

    public int insert(McpServerRegistry row) {
        return mapper.insert(row);
    }

    public McpServerRegistry findByIdAndTenant(long id, long tenantId) {
        return mapper.selectOne(
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getId, id)
                        .eq(McpServerRegistry::getTenantId, tenantId));
    }

    public long countByTenantAndName(long tenantId, String name, Long excludeId) {
        var q =
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getTenantId, tenantId)
                        .eq(McpServerRegistry::getName, name);
        if (excludeId != null) {
            q.ne(McpServerRegistry::getId, excludeId);
        }
        return mapper.selectCount(q);
    }

    public int updateById(McpServerRegistry row) {
        return mapper.updateById(row);
    }

    public int deleteByIdAndTenant(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getId, id)
                        .eq(McpServerRegistry::getTenantId, tenantId));
    }

    public java.util.List<McpServerRegistry> listActiveByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getTenantId, tenantId)
                        .eq(McpServerRegistry::getStatus, com.aaron.cloud.common.api.enums.mcp.McpServerStatus.ACTIVE)
                        .orderByAsc(McpServerRegistry::getName));
    }

    public java.util.List<McpServerRegistry> listActiveByTenantAndIds(long tenantId, java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return listActiveByTenant(tenantId);
        }
        return mapper.selectList(
                Wrappers.<McpServerRegistry>lambdaQuery()
                        .eq(McpServerRegistry::getTenantId, tenantId)
                        .eq(McpServerRegistry::getStatus, com.aaron.cloud.common.api.enums.mcp.McpServerStatus.ACTIVE)
                        .in(McpServerRegistry::getId, ids)
                        .orderByAsc(McpServerRegistry::getName));
    }
}
