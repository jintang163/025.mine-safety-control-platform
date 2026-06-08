package com.mine.safety.repository;

import com.mine.safety.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByAlertNo(String alertNo);

    Page<Alert> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    Page<Alert> findByLevelOrderByCreatedAtDesc(String level, Pageable pageable);

    Page<Alert> findBySensorIdOrderByCreatedAtDesc(String sensorId, Pageable pageable);

    List<Alert> findBySensorIdAndStatusAndLevelAndFirstAlertTimeAfter(
            String sensorId, Integer status, String level, LocalDateTime startTime);

    @Query("SELECT a FROM Alert a WHERE a.sensorId = :sensorId AND a.ruleId = :ruleId " +
           "AND a.status IN (0, 1) ORDER BY a.firstAlertTime DESC LIMIT 1")
    Alert findActiveAlert(@Param("sensorId") String sensorId, @Param("ruleId") Long ruleId);

    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.acknowledgedBy = :acknowledgedBy, " +
           "a.acknowledgedAt = :acknowledgedAt, a.acknowledgedComment = :acknowledgedComment " +
           "WHERE a.alertNo = :alertNo")
    int acknowledgeAlert(@Param("alertNo") String alertNo,
                         @Param("status") Integer status,
                         @Param("acknowledgedBy") String acknowledgedBy,
                         @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
                         @Param("acknowledgedComment") String acknowledgedComment);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = :status")
    long countByStatus(@Param("status") Integer status);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.level = :level AND a.createdAt >= :time")
    long countByLevelAndTimeAfter(@Param("level") String level, @Param("time") LocalDateTime time);

    @Modifying
    @Query("UPDATE Alert a SET a.lastAlertTime = :lastAlertTime, a.alertCount = a.alertCount + 1 " +
           "WHERE a.id = :alertId")
    int updateAlertFrequency(@Param("alertId") Long alertId, @Param("lastAlertTime") LocalDateTime lastAlertTime);

    List<Alert> findTop10ByOrderByCreatedAtDesc();
}
