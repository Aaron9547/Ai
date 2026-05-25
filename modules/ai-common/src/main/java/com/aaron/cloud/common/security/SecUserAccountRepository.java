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

    public Optional<SecUserAccount> findByAccountNo(String accountNo) {
        return selectOneByColumn(SecUserAccount::getAccountNo, accountNo);
    }

    public Optional<SecUserAccount> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return selectOneByColumn(SecUserAccount::getEmail, email);
    }

    public Optional<SecUserAccount> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        return selectOneByColumn(SecUserAccount::getPhone, phone);
    }

    /**
     * 登录标识解析：依次尝试邮箱、手机、账号编号、登录名（与开放登录 {@code loginName} 字段兼容）。
     */
    public Optional<SecUserAccount> findByLoginIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String raw = identifier.trim();
        if (raw.contains("@")) {
            String em = UserAccountProfileSupport.normalizeEmail(raw);
            if (em != null) {
                var byEmail = findByEmail(em);
                if (byEmail.isPresent()) {
                    return byEmail;
                }
            }
        }
        String phone = UserAccountProfileSupport.normalizePhone(raw);
        if (phone != null && UserAccountProfileSupport.isValidCnPhone(phone)) {
            var byPhone = findByPhone(phone);
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }
        if (raw.startsWith("U") && raw.length() >= 8) {
            var byNo = findByAccountNo(raw);
            if (byNo.isPresent()) {
                return byNo;
            }
        }
        return findByLoginName(raw);
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
        return existsColumn(SecUserAccount::getLoginName, loginName, excludeId);
    }

    public boolean existsAccountNo(String accountNo, Long excludeId) {
        return existsColumn(SecUserAccount::getAccountNo, accountNo, excludeId);
    }

    public boolean existsEmail(String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return existsColumn(SecUserAccount::getEmail, email, excludeId);
    }

    public boolean existsPhone(String phone, Long excludeId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return existsColumn(SecUserAccount::getPhone, phone, excludeId);
    }

    private Optional<SecUserAccount> selectOneByColumn(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<SecUserAccount, ?> column, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (accountTableSchema.hasJwtSeqColumn()) {
            return Optional.ofNullable(
                    mapper.selectOne(Wrappers.<SecUserAccount>lambdaQuery().eq(column, value)));
        }
        return Optional.ofNullable(
                mapper.selectOne(withoutJwtSeqSelect(Wrappers.<SecUserAccount>lambdaQuery()).eq(column, value)));
    }

    private boolean existsColumn(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<SecUserAccount, ?> column,
            String value,
            Long excludeId) {
        if (value == null || value.isBlank()) {
            return false;
        }
        var q = Wrappers.<SecUserAccount>lambdaQuery().eq(column, value);
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

    /** 登录成功后更新最近登录时间、IP 与地区（见 {@link com.aaron.cloud.common.web.LoginRegionResolver}）。 */
    public int updateLastLogin(long userId, java.time.LocalDateTime atUtc, String ip, String region) {
        String ipV = ip == null ? null : (ip.length() > 64 ? ip.substring(0, 64) : ip);
        String regV = region == null ? null : (region.length() > 128 ? region.substring(0, 128) : region);
        return mapper.update(
                null,
                Wrappers.<SecUserAccount>lambdaUpdate()
                        .eq(SecUserAccount::getId, userId)
                        .set(SecUserAccount::getLastLoginAt, atUtc)
                        .set(SecUserAccount::getLastLoginIp, ipV)
                        .set(SecUserAccount::getLastLoginRegion, regV));
    }

    /** 当前租户在册成员按账号 {@code last_login_region} 聚合（空归并为「—」）。 */
    public List<Map<String, Object>> countActiveMembersByLastLoginRegion(long tenantId) {
        return mapper.countActiveMembersByLastLoginRegion(tenantId);
    }
}
