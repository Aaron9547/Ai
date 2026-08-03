package com.aaron.cloud.common.observability.mapper;

import com.aaron.cloud.common.observability.entity.ObsMcpTraceEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ObsMcpTraceEventMapper extends BaseMapper<ObsMcpTraceEvent> {}
