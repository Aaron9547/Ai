package com.aaron.cloud.common.platform;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.entity.SysPlatformSetting;
import com.aaron.cloud.common.platform.mapper.SysPlatformSettingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysPlatformSettingRepository {

    private final SysPlatformSettingMapper mapper;

    public List<SysPlatformSetting> listAll() {
        return mapper.selectList(Wrappers.<SysPlatformSetting>lambdaQuery().orderByAsc(SysPlatformSetting::getSettingKey));
    }

    public Optional<SysPlatformSetting> find(PlatformSettingKey key) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysPlatformSetting>lambdaQuery().eq(SysPlatformSetting::getSettingKey, key)));
    }

    public int insert(SysPlatformSetting row) {
        return mapper.insert(row);
    }

    public int updateById(SysPlatformSetting row) {
        return mapper.updateById(row);
    }
}
