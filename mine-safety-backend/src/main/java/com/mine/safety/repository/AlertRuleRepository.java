package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertRuleRepository extends BaseMapper<AlertRule> {

    @Select("SELECT * FROM alert_rules WHERE enabled = 1 AND (sensor_type = #{sensorType} OR sensor_type IS NULL) AND (sensor_id = #{sensorId} OR sensor_id IS NULL)")
    List<AlertRule> findMatchingRules(@Param("sensorType") String sensorType,
                                      @Param("sensorId") String sensorId);
}
