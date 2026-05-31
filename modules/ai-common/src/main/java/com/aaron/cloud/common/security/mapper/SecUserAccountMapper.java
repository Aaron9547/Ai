package com.aaron.cloud.common.security.mapper;

import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecUserAccountMapper extends BaseMapper<SecUserAccount> {

    @Select(
            """
            SELECT COALESCE(NULLIF(TRIM(u.last_login_region), ''), '—') AS bucket, COUNT(1) AS cnt
            FROM sys_tenant_member m
            INNER JOIN sec_user_account u ON u.id = m.user_id
            WHERE m.tenant_id = #{tenantId} AND m.status = 1
            GROUP BY bucket
            ORDER BY cnt DESC
            LIMIT 80
            """)
    List<Map<String, Object>> countActiveMembersByLastLoginRegion(@Param("tenantId") long tenantId);

    @Select(
            """
            SELECT TRIM(u.last_login_region) AS region, TRIM(u.last_login_ip) AS ip
            FROM sys_tenant_member m
            INNER JOIN sec_user_account u ON u.id = m.user_id
            WHERE m.tenant_id = #{tenantId} AND m.status = 1
            """)
    List<Map<String, Object>> listActiveMemberLoginGeo(@Param("tenantId") long tenantId);
}
