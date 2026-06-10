package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.SensorMaintenanceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorMaintenanceRecordRepository extends BaseMapper<SensorMaintenanceRecord> {
}
