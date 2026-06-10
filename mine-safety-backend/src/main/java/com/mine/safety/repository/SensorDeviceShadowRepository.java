package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.SensorDeviceShadow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorDeviceShadowRepository extends BaseMapper<SensorDeviceShadow> {
}
