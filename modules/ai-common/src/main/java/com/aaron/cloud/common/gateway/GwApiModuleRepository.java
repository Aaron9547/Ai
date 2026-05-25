package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwApiModule;
import com.aaron.cloud.common.gateway.mapper.GwApiModuleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwApiModuleRepository {

    private final GwApiModuleMapper mapper;

    public List<GwApiModule> listAllOrdered() {
        return mapper.selectList(
                Wrappers.<GwApiModule>lambdaQuery()
                        .orderByAsc(GwApiModule::getSortOrder)
                        .orderByAsc(GwApiModule::getId));
    }

    public List<GwApiModule> listEnabledOrdered() {
        return mapper.selectList(
                Wrappers.<GwApiModule>lambdaQuery()
                        .eq(GwApiModule::getEnabled, ToggleState.ON)
                        .orderByAsc(GwApiModule::getSortOrder)
                        .orderByAsc(GwApiModule::getId));
    }

    public GwApiModule findById(long id) {
        return mapper.selectById(id);
    }

    public GwApiModule findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<GwApiModule>lambdaQuery().eq(GwApiModule::getCode, code.trim().toUpperCase()));
    }

    public long countByCode(String code, Long excludeId) {
        var q = Wrappers.<GwApiModule>lambdaQuery().eq(GwApiModule::getCode, code.trim().toUpperCase());
        if (excludeId != null) {
            q.ne(GwApiModule::getId, excludeId);
        }
        return mapper.selectCount(q);
    }

    public int insert(GwApiModule row) {
        return mapper.insert(row);
    }

    public int updateById(GwApiModule row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
