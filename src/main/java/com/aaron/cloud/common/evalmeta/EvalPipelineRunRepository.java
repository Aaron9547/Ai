package com.aaron.cloud.common.evalmeta;

import com.aaron.cloud.common.evalmeta.entity.EvalPipelineRun;
import com.aaron.cloud.common.evalmeta.mapper.EvalPipelineRunMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EvalPipelineRunRepository {

    private final EvalPipelineRunMapper mapper;

    public Page<EvalPipelineRun> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<EvalPipelineRun>lambdaQuery()
                        .eq(EvalPipelineRun::getTenantId, tenantId)
                        .orderByDesc(EvalPipelineRun::getUpdatedAt));
    }

    public int insert(EvalPipelineRun row) {
        return mapper.insert(row);
    }
}
