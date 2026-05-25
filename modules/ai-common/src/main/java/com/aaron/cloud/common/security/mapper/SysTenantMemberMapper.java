package com.aaron.cloud.common.security.mapper;

import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysTenantMemberMapper extends BaseMapper<SysTenantMember> {

    @Select(
            "<script>"
                    + "SELECT m.* FROM sys_tenant_member m "
                    + "INNER JOIN sec_user_account u ON u.id = m.user_id "
                    + "WHERE m.tenant_id = #{tid} "
                    + "<if test=\"kw != null and kw != ''\">"
                    + "AND (u.login_name LIKE CONCAT('%',#{kw},'%') OR IFNULL(u.display_name,'') LIKE CONCAT('%',#{kw},'%')) "
                    + "</if>"
                    + "ORDER BY m.updated_at DESC"
                    + "</script>")
    IPage<SysTenantMember> selectPageWithAccountKeyword(Page<SysTenantMember> page, @Param("tid") long tid, @Param("kw") String kw);
}
