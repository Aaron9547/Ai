package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey;
import com.aaron.cloud.common.tenant.runtime.entity.TenRuntimeSetting;
import com.aaron.cloud.common.tenant.runtime.mapper.TenRuntimeSettingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenRuntimeSettingRepository {

    private final TenRuntimeSettingMapper mapper;

    public Optional<TenRuntimeSetting> find(long tenantId, TenantRuntimeSettingKey key) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenRuntimeSetting>lambdaQuery()
                                .eq(TenRuntimeSetting::getTenantId, tenantId)
                                .eq(TenRuntimeSetting::getSettingKey, key)));
    }

    public int insert(TenRuntimeSetting row) {
        return mapper.insert(row);
    }

    public int updateById(TenRuntimeSetting row) {
        return mapper.updateById(row);
    }
}
