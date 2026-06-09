package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.Sensor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SensorRepository extends BaseMapper<Sensor> {

    @Update("UPDATE sensors SET status = #{status}, last_online_time = #{lastOnlineTime} WHERE sensor_id = #{sensorId}")
    int updateSensorStatus(@Param("sensorId") String sensorId,
                           @Param("status") Integer status,
                           @Param("lastOnlineTime") LocalDateTime lastOnlineTime);

    @Select("SELECT * FROM sensors WHERE status = #{status} AND last_online_time < #{timeout}")
    List<Sensor> findSensorsToCheckOffline(@Param("status") Integer status,
                                            @Param("timeout") LocalDateTime timeout);
}
