package com.aaron.cloud.common.audit.mapper;

import com.aaron.cloud.common.audit.entity.SysAuditEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysAuditEventMapper extends BaseMapper<SysAuditEvent> {}
