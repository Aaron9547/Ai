package com.aaron.cloud.common.gateway;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.entity.GwApiRateLimitRule;
import com.aaron.cloud.common.gateway.mapper.GwApiRateLimitRuleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GwApiRateLimitRuleRepository {

    private final GwApiRateLimitRuleMapper mapper;

    /** 管理端列表：仅指定租户的自有规则（不含 tenant_id 为 NULL 的全局规则，避免跨租户观测混排）。 */
    public Page<GwApiRateLimitRule> pageForTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<GwApiRateLimitRule>lambdaQuery()
                        .eq(GwApiRateLimitRule::getTenantId, tenantId)
                        .orderByDesc(GwApiRateLimitRule::getId));
    }

    /** 管理端：仅 {@code tenant_id IS NULL} 的全局限流规则。 */
    public Page<GwApiRateLimitRule> pageWhereTenantIdIsNull(long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<GwApiRateLimitRule>lambdaQuery()
                        .isNull(GwApiRateLimitRule::getTenantId)
                        .orderByDesc(GwApiRateLimitRule::getId));
    }

    /** 启用的规则，路径越长越优先（粗匹配在后）。 */
    public List<GwApiRateLimitRule> listEnabledForMatching() {
        List<GwApiRateLimitRule> rows =
                mapper.selectList(
                        Wrappers.<GwApiRateLimitRule>lambdaQuery()
                                .eq(GwApiRateLimitRule::getEnabled, ToggleState.ON));
        rows.sort(
                (a, b) ->
                        Integer.compare(
                                b.getPathPattern() == null ? 0 : b.getPathPattern().length(),
                                a.getPathPattern() == null ? 0 : a.getPathPattern().length()));
        return rows;
    }

    public GwApiRateLimitRule findById(long id) {
        return mapper.selectById(id);
    }

    public int insert(GwApiRateLimitRule row) {
        return mapper.insert(row);
    }

    public int updateById(GwApiRateLimitRule row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
