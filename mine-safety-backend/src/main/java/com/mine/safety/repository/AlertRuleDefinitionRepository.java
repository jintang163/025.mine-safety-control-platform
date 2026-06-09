package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.AlertRuleDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertRuleDefinitionRepository extends BaseMapper<AlertRuleDefinition> {

    @Select("SELECT * FROM alert_rule_definitions WHERE enabled = true AND (sensor_type = #{sensorType} OR sensor_type IS NULL) AND (sensor_id = #{sensorId} OR sensor_id IS NULL) AND (zone_code = #{zoneCode} OR zone_code IS NULL) ORDER BY CASE rule_type WHEN 'COMPOUND' THEN 1 WHEN 'TREND' THEN 2 WHEN 'SINGLE_THRESHOLD' THEN 3 END, level DESC")
    List<AlertRuleDefinition> findMatchingRules(@Param("sensorType") String sensorType,
                                                @Param("sensorId") String sensorId,
                                                @Param("zoneCode") String zoneCode);

    @Select("SELECT rule_code FROM alert_rule_definitions WHERE enabled = true")
    List<String> findAllEnabledRuleCodes();
}
