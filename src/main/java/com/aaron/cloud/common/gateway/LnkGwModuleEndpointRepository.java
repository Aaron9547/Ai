package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.gateway.entity.LnkGwModuleEndpoint;
import com.aaron.cloud.common.gateway.mapper.LnkGwModuleEndpointMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LnkGwModuleEndpointRepository {

    private final LnkGwModuleEndpointMapper mapper;

    public List<LnkGwModuleEndpoint> listByModuleId(long moduleId) {
        return mapper.selectList(
                Wrappers.<LnkGwModuleEndpoint>lambdaQuery().eq(LnkGwModuleEndpoint::getModuleId, moduleId));
    }

    public List<LnkGwModuleEndpoint> listByEndpointId(long endpointId) {
        return mapper.selectList(
                Wrappers.<LnkGwModuleEndpoint>lambdaQuery().eq(LnkGwModuleEndpoint::getEndpointId, endpointId));
    }

    public int deleteByModuleId(long moduleId) {
        return mapper.delete(
                Wrappers.<LnkGwModuleEndpoint>lambdaQuery().eq(LnkGwModuleEndpoint::getModuleId, moduleId));
    }

    public int insert(LnkGwModuleEndpoint row) {
        return mapper.insert(row);
    }

    public long countByModuleAndEndpoint(long moduleId, long endpointId) {
        return mapper.selectCount(
                Wrappers.<LnkGwModuleEndpoint>lambdaQuery()
                        .eq(LnkGwModuleEndpoint::getModuleId, moduleId)
                        .eq(LnkGwModuleEndpoint::getEndpointId, endpointId));
    }
}
