package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.profile.entity.TenUserMemoryAbstract;
import com.aaron.cloud.common.profile.mapper.TenUserMemoryAbstractMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserMemoryAbstractRepository {

    private final TenUserMemoryAbstractMapper mapper;

    public Optional<TenUserMemoryAbstract> findByTenantAndSubject(long tenantId, String subjectKey) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenUserMemoryAbstract>lambdaQuery()
                                .eq(TenUserMemoryAbstract::getTenantId, tenantId)
                                .eq(TenUserMemoryAbstract::getSubjectKey, subjectKey)));
    }

    public int insert(TenUserMemoryAbstract row) {
        return mapper.insert(row);
    }

    public int updateById(TenUserMemoryAbstract row) {
        return mapper.updateById(row);
    }

    public int deleteByTenantAndSubject(long tenantId, String subjectKey) {
        return mapper.delete(
                Wrappers.<TenUserMemoryAbstract>lambdaQuery()
                        .eq(TenUserMemoryAbstract::getTenantId, tenantId)
                        .eq(TenUserMemoryAbstract::getSubjectKey, subjectKey));
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
