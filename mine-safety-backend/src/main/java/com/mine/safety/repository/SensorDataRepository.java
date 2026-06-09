package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.SensorData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SensorDataRepository extends BaseMapper<SensorData> {

    @Select("SELECT AVG(value) FROM sensor_data WHERE sensor_id = #{sensorId} AND timestamp BETWEEN #{startTime} AND #{endTime}")
    BigDecimal findAverageValueBySensorIdAndTimeRange(@Param("sensorId") String sensorId,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    @Select("SELECT MAX(value) FROM sensor_data WHERE sensor_id = #{sensorId} AND timestamp BETWEEN #{startTime} AND #{endTime}")
    BigDecimal findMaxValueBySensorIdAndTimeRange(@Param("sensorId") String sensorId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM sensor_data WHERE sensor_id = #{sensorId} ORDER BY timestamp DESC LIMIT 1")
    SensorData findLatestBySensorId(@Param("sensorId") String sensorId);

    @Select("SELECT DISTINCT sensor_id FROM sensor_data WHERE timestamp >= #{time}")
    List<String> findActiveSensorIdsSince(@Param("time") LocalDateTime time);
}
