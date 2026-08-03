package com.aaron.cloud.common.observability.mapper;

import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ObsRagHitEventMapper extends BaseMapper<ObsRagHitEvent> {}
