package com.aaron.cloud.common.guardrail;

import com.aaron.cloud.common.guardrail.entity.GuardrailRule;
import com.aaron.cloud.common.guardrail.mapper.GuardrailRuleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GuardrailRuleRepository {

    private final GuardrailRuleMapper mapper;

    public List<GuardrailRule> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<GuardrailRule>lambdaQuery().eq(GuardrailRule::getTenantId, tenantId));
    }
}
