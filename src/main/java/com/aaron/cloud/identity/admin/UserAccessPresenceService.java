package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.accesslog.SysHttpAccessLogRepository;
import com.aaron.cloud.common.time.BeijingTime;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于 HTTP 访问日志推断「近期活跃/在线」；时间窗固定，与 {@link com.aaron.cloud.identity.rest.api.AdminUserRestController}
 * 列表展示一致。
 */
@Service
@RequiredArgsConstructor
public class UserAccessPresenceService {

    private static final int ONLINE_WITHIN_MINUTES = 10;

    private final SysHttpAccessLogRepository accessLogRepository;

    public Set<Long> onlineAmong(long tenantId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        LocalDateTime since = BeijingTime.nowLocal().minusMinutes(ONLINE_WITHIN_MINUTES);
        return accessLogRepository.findUserIdsWithRecentAccess(tenantId, userIds.stream().distinct().toList(), since);
    }
}
