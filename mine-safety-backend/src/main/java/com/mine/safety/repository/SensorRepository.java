package com.mine.safety.repository;

import com.mine.safety.domain.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findBySensorId(String sensorId);

    List<Sensor> findByType(String type);

    List<Sensor> findByStatus(Integer status);

    List<Sensor> findByTypeAndStatus(String type, Integer status);

    boolean existsBySensorId(String sensorId);

    @Modifying
    @Query("UPDATE Sensor s SET s.status = :status, s.lastOnlineTime = :lastOnlineTime WHERE s.sensorId = :sensorId")
    int updateSensorStatus(@Param("sensorId") String sensorId,
                           @Param("status") Integer status,
                           @Param("lastOnlineTime") LocalDateTime lastOnlineTime);

    @Query("SELECT s FROM Sensor s WHERE s.status = :status AND s.lastOnlineTime < :timeout")
    List<Sensor> findSensorsToCheckOffline(@Param("status") Integer status,
                                            @Param("timeout") LocalDateTime timeout);
}
