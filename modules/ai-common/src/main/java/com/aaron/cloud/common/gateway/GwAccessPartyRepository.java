package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.mapper.GwAccessPartyMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwAccessPartyRepository {

    private final GwAccessPartyMapper mapper;

    public Page<GwAccessParty> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<GwAccessParty>lambdaQuery()
                        .eq(GwAccessParty::getTenantId, tenantId)
                        .orderByDesc(GwAccessParty::getId));
    }

    public GwAccessParty findById(long id) {
        return mapper.selectById(id);
    }

    public GwAccessParty findByAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<GwAccessParty>lambdaQuery().eq(GwAccessParty::getAppId, appId.trim()));
    }

    public long countByAppId(String appId, Long excludeId) {
        var q = Wrappers.<GwAccessParty>lambdaQuery().eq(GwAccessParty::getAppId, appId.trim());
        if (excludeId != null) {
            q.ne(GwAccessParty::getId, excludeId);
        }
        return mapper.selectCount(q);
    }

    public List<GwAccessParty> listEnabledByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<GwAccessParty>lambdaQuery()
                        .eq(GwAccessParty::getTenantId, tenantId)
                        .eq(GwAccessParty::getStatus, ToggleState.ON)
                        .orderByAsc(GwAccessParty::getId));
    }

    public List<GwAccessParty> listAllEnabled() {
        return mapper.selectList(
                Wrappers.<GwAccessParty>lambdaQuery()
                        .eq(GwAccessParty::getStatus, ToggleState.ON)
                        .orderByAsc(GwAccessParty::getId));
    }

    public int insert(GwAccessParty row) {
        return mapper.insert(row);
    }

    public int updateById(GwAccessParty row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
