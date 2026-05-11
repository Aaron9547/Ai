package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.gateway.mapper.GwApiEndpointMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwApiEndpointRepository {

    private final GwApiEndpointMapper mapper;

    public Page<GwApiEndpoint> pageAll(long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<GwApiEndpoint>lambdaQuery().orderByDesc(GwApiEndpoint::getId));
    }

    /** 限流表单快捷选择：仅启用项，排序稳定。 */
    public List<GwApiEndpoint> listPicker() {
        return mapper.selectList(
                Wrappers.<GwApiEndpoint>lambdaQuery()
                        .eq(GwApiEndpoint::getEnabled, ToggleState.ON)
                        .orderByAsc(GwApiEndpoint::getSortOrder)
                        .orderByAsc(GwApiEndpoint::getId));
    }

    public GwApiEndpoint findById(long id) {
        return mapper.selectById(id);
    }

    public long countByPathAndMethod(String pathPattern, String httpMethod, Long excludeId) {
        var q =
                Wrappers.<GwApiEndpoint>lambdaQuery()
                        .eq(GwApiEndpoint::getPathPattern, pathPattern)
                        .eq(GwApiEndpoint::getHttpMethod, httpMethod);
        if (excludeId != null) {
            q.ne(GwApiEndpoint::getId, excludeId);
        }
        return mapper.selectCount(q);
    }

    public int insert(GwApiEndpoint row) {
        return mapper.insert(row);
    }

    public int updateById(GwApiEndpoint row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
