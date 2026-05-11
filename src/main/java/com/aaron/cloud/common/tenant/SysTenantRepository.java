package com.aaron.cloud.common.tenant;

import com.aaron.cloud.common.api.enums.TenantStatus;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import com.aaron.cloud.common.tenant.mapper.SysTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysTenantRepository {

    private final SysTenantMapper mapper;

    public Optional<SysTenant> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 批量加载租户主表行（仅命中 id；用于管理端列表一次填充 code/name）。
     */
    public Map<Long, SysTenant> mapTenantsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<SysTenant> rows = mapper.selectByIds(ids);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysTenant> out = HashMap.newHashMap(rows.size());
        for (SysTenant t : rows) {
            if (t.getId() != null) {
                out.put(t.getId(), t);
            }
        }
        return out;
    }

    /**
     * 批量解析租户主键 → {@code code}（仅命中行返回；用于管理端列表避免 N+1）。
     */
    public Map<Long, String> mapTenantCodeByIds(Collection<Long> ids) {
        Map<Long, SysTenant> byId = mapTenantsByIds(ids);
        if (byId.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = HashMap.newHashMap(byId.size());
        for (var e : byId.entrySet()) {
            String c = e.getValue().getCode();
            if (c != null && !c.isBlank()) {
                out.put(e.getKey(), c);
            }
        }
        return out;
    }

    /** 批量解析租户主键 → {@code name}（展示名；无则空串不放入 map）。 */
    public Map<Long, String> mapTenantNameByIds(Collection<Long> ids) {
        Map<Long, SysTenant> byId = mapTenantsByIds(ids);
        if (byId.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = HashMap.newHashMap(byId.size());
        for (var e : byId.entrySet()) {
            String n = e.getValue().getName();
            if (n != null && !n.isBlank()) {
                out.put(e.getKey(), n);
            }
        }
        return out;
    }

    public Optional<SysTenant> findByCode(String code) {
        return Optional.ofNullable(
                mapper.selectOne(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getCode, code)));
    }

    public List<SysTenant> findAllActive() {
        LambdaQueryWrapper<SysTenant> q =
                Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getStatus, TenantStatus.ACTIVE);
        return mapper.selectList(q);
    }

    public List<SysTenant> listAllOrderById() {
        return mapper.selectList(Wrappers.<SysTenant>lambdaQuery().orderByAsc(SysTenant::getId));
    }

    public int insert(SysTenant row) {
        return mapper.insert(row);
    }

    public int updateById(SysTenant row) {
        return mapper.updateById(row);
    }
}
