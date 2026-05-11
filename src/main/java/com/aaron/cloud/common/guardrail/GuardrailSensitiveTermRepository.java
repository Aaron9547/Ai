package com.aaron.cloud.common.guardrail;

import com.aaron.cloud.common.api.enums.GuardrailSensitivePoolType;
import com.aaron.cloud.common.guardrail.entity.GuardrailSensitiveTerm;
import com.aaron.cloud.common.guardrail.mapper.GuardrailSensitiveTermMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GuardrailSensitiveTermRepository {

    private final GuardrailSensitiveTermMapper mapper;

    public List<String> listEffectiveWordsForChatTenant(long tenantId) {
        List<GuardrailSensitiveTerm> platform =
                mapper.selectList(
                        Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                                .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.PLATFORM)
                                .eq(GuardrailSensitiveTerm::getTenantId, 0L));
        List<GuardrailSensitiveTerm> tenant =
                mapper.selectList(
                        Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                                .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.TENANT)
                                .eq(GuardrailSensitiveTerm::getTenantId, tenantId));
        List<String> out = new ArrayList<>(platform.size() + tenant.size());
        for (GuardrailSensitiveTerm t : platform) {
            out.add(t.getWord());
        }
        for (GuardrailSensitiveTerm t : tenant) {
            out.add(t.getWord());
        }
        return out;
    }

    public List<GuardrailSensitiveTerm> listPlatformTerms() {
        return mapper.selectList(
                Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                        .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.PLATFORM)
                        .eq(GuardrailSensitiveTerm::getTenantId, 0L)
                        .orderByAsc(GuardrailSensitiveTerm::getId));
    }

    public List<GuardrailSensitiveTerm> listTenantTerms(long tenantId) {
        return mapper.selectList(
                Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                        .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.TENANT)
                        .eq(GuardrailSensitiveTerm::getTenantId, tenantId)
                        .orderByAsc(GuardrailSensitiveTerm::getId));
    }

    public Page<GuardrailSensitiveTerm> pagePlatformTerms(
            long pageNo, long pageSize, String keywordContainsOrNull) {
        var q =
                Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                        .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.PLATFORM)
                        .eq(GuardrailSensitiveTerm::getTenantId, 0L)
                        .orderByAsc(GuardrailSensitiveTerm::getId);
        if (keywordContainsOrNull != null && !keywordContainsOrNull.isBlank()) {
            q.like(GuardrailSensitiveTerm::getWord, keywordContainsOrNull.strip());
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public Page<GuardrailSensitiveTerm> pageTenantTerms(
            long tenantId, long pageNo, long pageSize, String keywordContainsOrNull) {
        var q =
                Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                        .eq(GuardrailSensitiveTerm::getPoolType, GuardrailSensitivePoolType.TENANT)
                        .eq(GuardrailSensitiveTerm::getTenantId, tenantId)
                        .orderByAsc(GuardrailSensitiveTerm::getId);
        if (keywordContainsOrNull != null && !keywordContainsOrNull.isBlank()) {
            q.like(GuardrailSensitiveTerm::getWord, keywordContainsOrNull.strip());
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public Optional<GuardrailSensitiveTerm> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public int insert(GuardrailSensitiveTerm row) {
        return mapper.insert(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    public boolean exists(GuardrailSensitivePoolType poolType, long tenantId, String word) {
        return mapper.selectCount(
                        Wrappers.<GuardrailSensitiveTerm>lambdaQuery()
                                .eq(GuardrailSensitiveTerm::getPoolType, poolType)
                                .eq(GuardrailSensitiveTerm::getTenantId, tenantId)
                                .eq(GuardrailSensitiveTerm::getWord, word))
                > 0;
    }
}
