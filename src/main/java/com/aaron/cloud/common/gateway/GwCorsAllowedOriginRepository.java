package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwCorsAllowedOrigin;
import com.aaron.cloud.common.gateway.mapper.GwCorsAllowedOriginMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwCorsAllowedOriginRepository {

    private final GwCorsAllowedOriginMapper mapper;

    public List<GwCorsAllowedOrigin> listAllOrdered() {
        return mapper.selectList(
                Wrappers.<GwCorsAllowedOrigin>lambdaQuery()
                        .orderByAsc(GwCorsAllowedOrigin::getSortOrder)
                        .orderByAsc(GwCorsAllowedOrigin::getId));
    }

    public List<GwCorsAllowedOrigin> listEnabledOrdered() {
        return mapper.selectList(
                Wrappers.<GwCorsAllowedOrigin>lambdaQuery()
                        .eq(GwCorsAllowedOrigin::getEnabled, ToggleState.ON)
                        .orderByAsc(GwCorsAllowedOrigin::getSortOrder)
                        .orderByAsc(GwCorsAllowedOrigin::getId));
    }

    public GwCorsAllowedOrigin findById(long id) {
        return mapper.selectById(id);
    }

    /** 规范化后的精确匹配（调用方保证 origin 已规范化）。 */
    public GwCorsAllowedOrigin findByOriginExact(String normalizedOrigin) {
        return mapper.selectOne(
                Wrappers.<GwCorsAllowedOrigin>lambdaQuery()
                        .eq(GwCorsAllowedOrigin::getOrigin, normalizedOrigin)
                        .last("LIMIT 1"));
    }

    public int insert(GwCorsAllowedOrigin row) {
        return mapper.insert(row);
    }

    public int updateById(GwCorsAllowedOrigin row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
