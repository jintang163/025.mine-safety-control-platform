package com.mine.safety.repository;

import com.mine.safety.domain.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 传感器数据数据访问接口
 * 继承JpaRepository，提供传感器历史数据的CRUD操作和自定义查询。
 *
 * 主要功能：
 *   - 按时间范围查询历史数据
 *   - 分页查询传感器数据
 *   - 统计查询（平均值、最大值）
 *   - 查询最新数据
 *   - 查询活跃传感器ID
 *
 * 注意：
 *   - MySQL主要用于结构化数据存储和小范围历史查询
 *   - 大范围时序数据查询应使用InfluxDB
 */
@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    /**
     * 按传感器ID和时间范围查询历史数据（倒序）
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 历史数据列表（按时间倒序）
     */
    List<SensorData> findBySensorIdAndTimestampBetweenOrderByTimestampDesc(
            String sensorId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按传感器ID分页查询历史数据（倒序）
     *
     * @param sensorId 传感器ID
     * @param pageable 分页参数
     * @return 历史数据分页结果
     */
    Page<SensorData> findBySensorIdOrderByTimestampDesc(String sensorId, Pageable pageable);

    /**
     * 查询指定时间范围内的平均值
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 平均值
     */
    @Query("SELECT AVG(sd.value) FROM SensorData sd WHERE sd.sensorId = :sensorId AND sd.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal findAverageValueBySensorIdAndTimeRange(
            @Param("sensorId") String sensorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间范围内的最大值
     *
     * @param sensorId  传感器ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 最大值
     */
    @Query("SELECT MAX(sd.value) FROM SensorData sd WHERE sd.sensorId = :sensorId AND sd.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal findMaxValueBySensorIdAndTimeRange(
            @Param("sensorId") String sensorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询传感器的最新一条数据
     *
     * @param sensorId 传感器ID
     * @return 最新数据（如果存在）
     */
    @Query("SELECT sd FROM SensorData sd WHERE sd.sensorId = :sensorId ORDER BY sd.timestamp DESC LIMIT 1")
    SensorData findLatestBySensorId(@Param("sensorId") String sensorId);

    /**
     * 查询传感器最近100条数据
     *
     * @param sensorId 传感器ID
     * @return 最近100条数据
     */
    List<SensorData> findTop100BySensorIdOrderByTimestampDesc(String sensorId);

    /**
     * 查询指定时间以来有数据上报的传感器ID列表
     * 用于统计活跃传感器数量。
     *
     * @param time 开始时间
     * @return 活跃传感器ID列表
     */
    @Query(value = "SELECT DISTINCT sensor_id FROM sensor_data WHERE timestamp >= :time", nativeQuery = true)
    List<String> findActiveSensorIdsSince(@Param("time") LocalDateTime time);
}
