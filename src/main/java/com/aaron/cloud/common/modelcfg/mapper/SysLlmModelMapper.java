package com.aaron.cloud.common.modelcfg.mapper;

import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysLlmModelMapper extends BaseMapper<SysLlmModel> {

    /**
     * 在额度内累加已用 token（共用额度）；{@code token_quota_total IS NULL} 表示不限制。
     *
     * @return 影响行数，0 表示未更新（额度已满等）
     */
    @Update(
            """
            UPDATE llm_model SET tokens_used = COALESCE(tokens_used, 0) + #{delta}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            AND (token_quota_total IS NULL OR COALESCE(tokens_used, 0) + #{delta} <= token_quota_total)
            """)
    int addTokensUsedWithinQuota(
            @Param("tenantId") long tenantId, @Param("id") long id, @Param("delta") long delta);

    /** 异步落库累加（不在 SQL 中再次校验额度，额度以 Redis/Lua 或同步路径为准） */
    @Update(
            """
            UPDATE llm_model SET tokens_used = COALESCE(tokens_used, 0) + #{delta}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            """)
    int appendTokensUsed(@Param("tenantId") long tenantId, @Param("id") long id, @Param("delta") long delta);
}
