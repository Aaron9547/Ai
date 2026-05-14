package com.aaron.cloud.common.security;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.security.mapper.SysTenantMemberMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysTenantMemberRepository {

    private final SysTenantMemberMapper mapper;

    public List<SysTenantMember> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .orderByDesc(SysTenantMember::getUpdatedAt));
    }

    /** 管理端用户列表：仅当前租户成员行（含已退出等状态），按成员行更新时间倒序分页。 */
    public Page<SysTenantMember> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .orderByDesc(SysTenantMember::getUpdatedAt));
    }

    /** 当前租户内 {@link UserAccountStatus#ACTIVE} 成员行数。 */
    public long countActiveByTenant(long tenantId) {
        return mapper.selectCount(
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .eq(SysTenantMember::getStatus, UserAccountStatus.ACTIVE));
    }

    /** 关键词匹配登录名或昵称（不含 %/_ 注入片段）；关键词为空时等价 {@link #pageByTenant}。 */
    public IPage<SysTenantMember> pageByTenantAndUserKeyword(
            long tenantId, long pageNo, long pageSize, String keyword) {
        String kw = sanitizeKeyword(keyword);
        if (kw.isEmpty()) {
            return pageByTenant(tenantId, pageNo, pageSize);
        }
        Page<SysTenantMember> pg = Page.of(pageNo, pageSize);
        return mapper.selectPageWithAccountKeyword(pg, tenantId, kw);
    }

    private static String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String s = keyword.trim();
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s.replace("%", "").replace("_", "").replace("'", "").replace("\\", "");
    }

    /** 按租户列出成员；{@code role} 非空时仅该角色；{@code memberStatus} 非空时仅该成员状态。 */
    public List<SysTenantMember> listByTenantAndRoleAndStatus(
            long tenantId, TenantMemberRole role, UserAccountStatus memberStatus) {
        var q =
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getTenantId, tenantId)
                        .orderByAsc(SysTenantMember::getId);
        if (role != null) {
            q.eq(SysTenantMember::getRoleCode, role);
        }
        if (memberStatus != null) {
            q.eq(SysTenantMember::getStatus, memberStatus);
        }
        return mapper.selectList(q);
    }

    public List<SysTenantMember> listByUserId(long userId) {
        return mapper.selectList(
                Wrappers.<SysTenantMember>lambdaQuery()
                        .eq(SysTenantMember::getUserId, userId)
                        .orderByDesc(SysTenantMember::getUpdatedAt));
    }

    public Optional<SysTenantMember> find(long tenantId, long userId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<SysTenantMember>lambdaQuery()
                                .eq(SysTenantMember::getTenantId, tenantId)
                                .eq(SysTenantMember::getUserId, userId)));
    }

    /**
     * 批量判断：给定 {@code userIds} 中哪些在指定租户下存在成员行（任意成员状态），用于管理端列表避免跨租户误用全局账号展示名。
     */
    public Set<Long> listUserIdsHavingMembership(long tenantId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Set.of();
        }
        List<SysTenantMember> rows =
                mapper.selectList(
                        Wrappers.<SysTenantMember>lambdaQuery()
                                .eq(SysTenantMember::getTenantId, tenantId)
                                .in(SysTenantMember::getUserId, distinct));
        if (rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(SysTenantMember::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public int insert(SysTenantMember row) {
        return mapper.insert(row);
    }

    public int updateById(SysTenantMember row) {
        return mapper.updateById(row);
    }

    public java.util.Optional<Long> findFirstTenantIdForUser(long userId) {
        var m =
                mapper.selectOne(
                        Wrappers.<SysTenantMember>lambdaQuery()
                                .eq(SysTenantMember::getUserId, userId)
                                .last("LIMIT 1"));
        return java.util.Optional.ofNullable(m).map(SysTenantMember::getTenantId);
    }

    public void ensureOwner(long tenantId, long userId) {
        if (find(tenantId, userId).isEmpty()) {
            var m = new SysTenantMember();
            m.setTenantId(tenantId);
            m.setUserId(userId);
            m.setRoleCode(TenantMemberRole.OWNER);
            m.setStatus(UserAccountStatus.ACTIVE);
            mapper.insert(m);
        }
    }
}
