package com.aaron.cloud.common.accesslog.mapper;

import com.aaron.cloud.common.accesslog.entity.SysHttpAccessLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysHttpAccessLogMapper extends BaseMapper<SysHttpAccessLog> {}
