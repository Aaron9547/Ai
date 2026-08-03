package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.mapper.GwAccessPartyGrantMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwAccessPartyGrantRepository {

    private final GwAccessPartyGrantMapper mapper;

    public GwAccessPartyGrant findById(long id) {
        return mapper.selectById(id);
    }

    public List<GwAccessPartyGrant> listByAccessPartyId(long accessPartyId) {
        return mapper.selectList(
                Wrappers.<GwAccessPartyGrant>lambdaQuery()
                        .eq(GwAccessPartyGrant::getAccessPartyId, accessPartyId)
                        .orderByAsc(GwAccessPartyGrant::getId));
    }

    public List<GwAccessPartyGrant> listEnabledByAccessPartyId(long accessPartyId) {
        return mapper.selectList(
                Wrappers.<GwAccessPartyGrant>lambdaQuery()
                        .eq(GwAccessPartyGrant::getAccessPartyId, accessPartyId)
                        .eq(GwAccessPartyGrant::getEnabled, ToggleState.ON));
    }

    public List<GwAccessPartyGrant> listAllEnabled() {
        return mapper.selectList(
                Wrappers.<GwAccessPartyGrant>lambdaQuery().eq(GwAccessPartyGrant::getEnabled, ToggleState.ON));
    }

    public GwAccessPartyGrant findByPartyAndEndpoint(long accessPartyId, long endpointId) {
        return mapper.selectOne(
                Wrappers.<GwAccessPartyGrant>lambdaQuery()
                        .eq(GwAccessPartyGrant::getAccessPartyId, accessPartyId)
                        .eq(GwAccessPartyGrant::getEndpointId, endpointId));
    }

    public int sumGrantedRpmByAccessParty(long accessPartyId, Long excludeGrantId) {
        var q =
                Wrappers.<GwAccessPartyGrant>lambdaQuery()
                        .select(GwAccessPartyGrant::getGrantedRpm)
                        .eq(GwAccessPartyGrant::getAccessPartyId, accessPartyId)
                        .eq(GwAccessPartyGrant::getEnabled, ToggleState.ON);
        if (excludeGrantId != null) {
            q.ne(GwAccessPartyGrant::getId, excludeGrantId);
        }
        return mapper.selectList(q).stream()
                .mapToInt(g -> g.getGrantedRpm() == null ? 0 : g.getGrantedRpm())
                .sum();
    }

    public int sumGrantedRpmByEndpoint(long endpointId, Long excludeGrantId) {
        var q =
                Wrappers.<GwAccessPartyGrant>lambdaQuery()
                        .select(GwAccessPartyGrant::getGrantedRpm)
                        .eq(GwAccessPartyGrant::getEndpointId, endpointId)
                        .eq(GwAccessPartyGrant::getEnabled, ToggleState.ON);
        if (excludeGrantId != null) {
            q.ne(GwAccessPartyGrant::getId, excludeGrantId);
        }
        return mapper.selectList(q).stream()
                .mapToInt(g -> g.getGrantedRpm() == null ? 0 : g.getGrantedRpm())
                .sum();
    }

    public int insert(GwAccessPartyGrant row) {
        return mapper.insert(row);
    }

    public int updateById(GwAccessPartyGrant row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    public int deleteByAccessPartyId(long accessPartyId) {
        return mapper.delete(
                Wrappers.<GwAccessPartyGrant>lambdaQuery().eq(GwAccessPartyGrant::getAccessPartyId, accessPartyId));
    }
}
