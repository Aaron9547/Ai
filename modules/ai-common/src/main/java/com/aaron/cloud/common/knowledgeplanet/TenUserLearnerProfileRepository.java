package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.knowledgeplanet.entity.TenUserLearnerProfile;
import com.aaron.cloud.common.knowledgeplanet.mapper.TenUserLearnerProfileMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserLearnerProfileRepository {

    private final TenUserLearnerProfileMapper mapper;

    public Optional<TenUserLearnerProfile> findByUser(long tenantId, long userId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<TenUserLearnerProfile>lambdaQuery()
                                .eq(TenUserLearnerProfile::getTenantId, tenantId)
                                .eq(TenUserLearnerProfile::getUserId, userId)));
    }

    public int insert(TenUserLearnerProfile row) {
        return mapper.insert(row);
    }

    public int updateById(TenUserLearnerProfile row) {
        return mapper.updateById(row);
    }
}
