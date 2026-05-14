package com.aaron.cloud.common.accesslog.mapper;

import com.aaron.cloud.common.accesslog.entity.SysHttpAccessLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysHttpAccessLogMapper extends BaseMapper<SysHttpAccessLog> {

    @Select(
            """
            SELECT l.client_ip AS clientIp,
                   COUNT(DISTINCT l.user_id) AS distinctUsers,
                   COUNT(1) AS hits
            FROM sys_http_access_log l
            WHERE l.tenant_id = #{tenantId}
              AND l.user_id IS NOT NULL
              AND l.created_at >= #{sinceUtc}
              AND l.client_ip IS NOT NULL
              AND TRIM(l.client_ip) <> ''
            GROUP BY l.client_ip
            ORDER BY hits DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topClientIpsByTenantSince(
            @Param("tenantId") long tenantId,
            @Param("sinceUtc") LocalDateTime sinceUtc,
            @Param("limit") int limit);
}
