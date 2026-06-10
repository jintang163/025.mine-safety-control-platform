package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.SensorCalibrationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorCalibrationRecordRepository extends BaseMapper<SensorCalibrationRecord> {
}
