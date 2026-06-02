package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.api.enums.profile.ProfileTagCode;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import com.aaron.cloud.common.profile.mapper.TenProfileTagMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenProfileTagRepository {

    private final TenProfileTagMapper mapper;

    public long countByTenantAndSubject(long tenantId, String subjectKey) {
        return mapper.selectCount(
                Wrappers.<TenProfileTag>lambdaQuery()
                        .eq(TenProfileTag::getTenantId, tenantId)
                        .eq(TenProfileTag::getSubjectKey, subjectKey));
    }

    public Optional<TenProfileTag> find(long tenantId, String subjectKey, ProfileTagCode code) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenProfileTag>lambdaQuery()
                                .eq(TenProfileTag::getTenantId, tenantId)
                                .eq(TenProfileTag::getSubjectKey, subjectKey)
                                .eq(TenProfileTag::getTagCode, code)));
    }

    public List<TenProfileTag> listByTenantAndSubjectKey(long tenantId, String subjectKey) {
        return mapper.selectList(
                Wrappers.<TenProfileTag>lambdaQuery()
                        .eq(TenProfileTag::getTenantId, tenantId)
                        .eq(TenProfileTag::getSubjectKey, subjectKey)
                        .orderByAsc(TenProfileTag::getTagCode));
    }

    public List<TenProfileTag> listByTenantAndTagCode(long tenantId, ProfileTagCode code) {
        return mapper.selectList(
                Wrappers.<TenProfileTag>lambdaQuery()
                        .eq(TenProfileTag::getTenantId, tenantId)
                        .eq(TenProfileTag::getTagCode, code)
                        .orderByDesc(TenProfileTag::getUpdatedAt));
    }

    public int insert(TenProfileTag row) {
        return mapper.insert(row);
    }

    public int updateById(TenProfileTag row) {
        return mapper.updateById(row);
    }

    public int deleteByTenantAndSubjectKey(long tenantId, String subjectKey) {
        return mapper.delete(
                Wrappers.<TenProfileTag>lambdaQuery()
                        .eq(TenProfileTag::getTenantId, tenantId)
                        .eq(TenProfileTag::getSubjectKey, subjectKey));
    }
}
