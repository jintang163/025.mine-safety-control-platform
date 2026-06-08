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

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    List<SensorData> findBySensorIdAndTimestampBetweenOrderByTimestampDesc(
            String sensorId, LocalDateTime startTime, LocalDateTime endTime);

    Page<SensorData> findBySensorIdOrderByTimestampDesc(String sensorId, Pageable pageable);

    @Query("SELECT AVG(sd.value) FROM SensorData sd WHERE sd.sensorId = :sensorId AND sd.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal findAverageValueBySensorIdAndTimeRange(
            @Param("sensorId") String sensorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT MAX(sd.value) FROM SensorData sd WHERE sd.sensorId = :sensorId AND sd.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal findMaxValueBySensorIdAndTimeRange(
            @Param("sensorId") String sensorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT sd FROM SensorData sd WHERE sd.sensorId = :sensorId ORDER BY sd.timestamp DESC LIMIT 1")
    SensorData findLatestBySensorId(@Param("sensorId") String sensorId);

    List<SensorData> findTop100BySensorIdOrderByTimestampDesc(String sensorId);

    @Query(value = "SELECT DISTINCT sensor_id FROM sensor_data WHERE timestamp >= :time", nativeQuery = true)
    List<String> findActiveSensorIdsSince(@Param("time") LocalDateTime time);
}
