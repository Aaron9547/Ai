package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.profile.entity.TenUserDeviceLink;
import com.aaron.cloud.common.profile.mapper.TenUserDeviceLinkMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserDeviceLinkRepository {

    private static final ZoneId DB_WALL_CLOCK = ZoneId.of("Asia/Shanghai");

    private final TenUserDeviceLinkMapper mapper;

    public Optional<TenUserDeviceLink> find(long tenantId, long userId, String deviceId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenUserDeviceLink>lambdaQuery()
                                .eq(TenUserDeviceLink::getTenantId, tenantId)
                                .eq(TenUserDeviceLink::getUserId, userId)
                                .eq(TenUserDeviceLink::getDeviceId, deviceId.trim())));
    }

    public int insertIfAbsent(TenUserDeviceLink row) {
        row.setLinkedAt(LocalDateTime.now(DB_WALL_CLOCK));
        if (find(row.getTenantId(), row.getUserId(), row.getDeviceId()).isPresent()) {
            return 0;
        }
        return mapper.insert(row);
    }

    public int deleteByTenantAndUser(long tenantId, long userId) {
        return mapper.delete(
                Wrappers.<TenUserDeviceLink>lambdaQuery()
                        .eq(TenUserDeviceLink::getTenantId, tenantId)
                        .eq(TenUserDeviceLink::getUserId, userId));
    }
}
