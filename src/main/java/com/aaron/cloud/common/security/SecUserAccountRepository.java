package com.aaron.cloud.common.security;

import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.mapper.SecUserAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SecUserAccountRepository {

    private final SecUserAccountMapper mapper;
    private final SecUserAccountTableSchema accountTableSchema;

    private static LambdaQueryWrapper<SecUserAccount> withoutJwtSeqSelect(
            LambdaQueryWrapper<SecUserAccount> base) {
        return base.select(SecUserAccount.class, fi -> !"jwtSeq".equals(fi.getProperty()));
    }

    public Optional<SecUserAccount> findByLoginName(String loginName) {
        if (accountTableSchema.hasJwtSeqColumn()) {
            return Optional.ofNullable(
                    mapper.selectOne(
                            Wrappers.<SecUserAccount>lambdaQuery()
                                    .eq(SecUserAccount::getLoginName, loginName)));
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        withoutJwtSeqSelect(Wrappers.<SecUserAccount>lambdaQuery())
                                .eq(SecUserAccount::getLoginName, loginName)));
    }

    /**
     * 管理端列表：主键 → 展示名（优先昵称，否则登录名）；未命中不放入 map。
     */
    public Map<Long, String> mapUserDisplayLabelByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SecUserAccount> rows;
        if (accountTableSchema.hasJwtSeqColumn()) {
            rows = mapper.selectByIds(ids);
        } else {
            rows =
                    mapper.selectList(
                            withoutJwtSeqSelect(Wrappers.<SecUserAccount>lambdaQuery())
                                    .in(SecUserAccount::getId, ids));
        }
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = HashMap.newHashMap(rows.size());
        for (SecUserAccount u : rows) {
            if (u.getId() == null) {
                continue;
            }
            String dn = u.getDisplayName();
            if (dn != null && !dn.isBlank()) {
                out.put(u.getId(), dn.trim());
            } else if (u.getLoginName() != null && !u.getLoginName().isBlank()) {
                out.put(u.getId(), u.getLoginName().trim());
            }
        }
        return out;
    }

    public Optional<SecUserAccount> findById(long id) {
        if (accountTableSchema.hasJwtSeqColumn()) {
            return Optional.ofNullable(mapper.selectById(id));
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        withoutJwtSeqSelect(Wrappers.<SecUserAccount>lambdaQuery())
                                .eq(SecUserAccount::getId, id)));
    }

    public Page<SecUserAccount> pageAll(long pageNo, long pageSize) {
        var q = Wrappers.<SecUserAccount>lambdaQuery().orderByDesc(SecUserAccount::getUpdatedAt);
        if (!accountTableSchema.hasJwtSeqColumn()) {
            withoutJwtSeqSelect(q);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public int insert(SecUserAccount row) {
        return mapper.insert(row);
    }

    public int updateById(SecUserAccount row) {
        return mapper.updateById(row);
    }

    public boolean existsLoginName(String loginName, Long excludeId) {
        var q = Wrappers.<SecUserAccount>lambdaQuery().eq(SecUserAccount::getLoginName, loginName);
        if (excludeId != null) {
            q.ne(SecUserAccount::getId, excludeId);
        }
        return mapper.selectCount(q) > 0;
    }

    /** 使用户当前及此前签发的 JWT 失效。 */
    public int incrementJwtSeq(long userId) {
        if (!accountTableSchema.hasJwtSeqColumn()) {
            return 0;
        }
        return mapper.update(
                null,
                Wrappers.<SecUserAccount>lambdaUpdate()
                        .setSql("jwt_seq = IFNULL(jwt_seq,0) + 1")
                        .eq(SecUserAccount::getId, userId));
    }
}
