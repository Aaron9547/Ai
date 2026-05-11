package com.aaron.cloud.common.security;

import com.aaron.cloud.common.security.entity.SysAdminMenuItem;
import com.aaron.cloud.common.security.mapper.SysAdminMenuItemMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SysAdminMenuItemRepository {

    private final SysAdminMenuItemMapper mapper;

    public List<SysAdminMenuItem> listAllOrderBySort() {
        return mapper.selectList(
                Wrappers.<SysAdminMenuItem>lambdaQuery().orderByAsc(SysAdminMenuItem::getSortOrder).orderByAsc(SysAdminMenuItem::getId));
    }

    public SysAdminMenuItem findById(long id) {
        return mapper.selectById(id);
    }

    public SysAdminMenuItem findByMenuCode(String menuCode) {
        return mapper.selectOne(
                Wrappers.<SysAdminMenuItem>lambdaQuery().eq(SysAdminMenuItem::getMenuCode, menuCode));
    }

    public int insert(SysAdminMenuItem row) {
        return mapper.insert(row);
    }

    public int updateById(SysAdminMenuItem row) {
        return mapper.updateById(row);
    }

    public int deleteById(long id) {
        return mapper.deleteById(id);
    }
}
