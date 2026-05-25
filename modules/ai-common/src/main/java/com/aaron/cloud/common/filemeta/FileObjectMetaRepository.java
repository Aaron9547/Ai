package com.aaron.cloud.common.filemeta;

import com.aaron.cloud.common.filemeta.entity.FileObjectMeta;
import com.aaron.cloud.common.filemeta.mapper.FileObjectMetaMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FileObjectMetaRepository {

    private final FileObjectMetaMapper mapper;

    public Page<FileObjectMeta> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<FileObjectMeta>lambdaQuery()
                        .eq(FileObjectMeta::getTenantId, tenantId)
                        .orderByDesc(FileObjectMeta::getCreatedAt));
    }

    public int insert(FileObjectMeta row) {
        return mapper.insert(row);
    }
}
