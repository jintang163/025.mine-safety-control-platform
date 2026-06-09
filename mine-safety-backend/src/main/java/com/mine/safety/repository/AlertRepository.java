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

/**
 * 报警记录数据访问接口
 * 继承JpaRepository，提供报警记录的CRUD操作和自定义查询
 *
 * 主要功能：
 *   - 按状态/级别/传感器分页查询报警
 *   - 查询活跃的报警（未处理或处理中）
 *   - 确认/处理报警
 *   - 统计报警数量
 *   - 更新报警频率（同一报警重复触发时）
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByAlertNo(String alertNo);

    Page<Alert> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    Page<Alert> findByLevelOrderByCreatedAtDesc(String level, Pageable pageable);

    Page<Alert> findBySensorIdOrderByCreatedAtDesc(String sensorId, Pageable pageable);

    List<Alert> findBySensorIdAndStatusAndLevelAndFirstAlertTimeAfter(
            String sensorId, Integer status, String level, LocalDateTime startTime);

    @Query("SELECT a FROM Alert a WHERE a.sensorId = :sensorId AND a.ruleId = :ruleId " +
           "AND a.status IN (0, 1, 4) ORDER BY a.firstAlertTime DESC LIMIT 1")
    Alert findActiveAlert(@Param("sensorId") String sensorId, @Param("ruleId") Long ruleId);

    @Query("SELECT a FROM Alert a WHERE a.sensorId = :sensorId AND a.ruleId = 0 " +
           "AND a.level = :level AND a.status IN (0, 1, 4) ORDER BY a.firstAlertTime DESC LIMIT 1")
    Alert findActiveThresholdAlert(@Param("sensorId") String sensorId, @Param("level") String level);

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

    @Query("SELECT a FROM Alert a WHERE a.status = 0 AND a.escalationLevel = :level " +
           "AND a.createdAt <= :thresholdTime")
    List<Alert> findUnconfirmedBeforeTime(@Param("level") String level, @Param("thresholdTime") LocalDateTime thresholdTime);

    @Query("SELECT a FROM Alert a WHERE a.status IN (0, 4)")
    List<Alert> findAllUnconfirmedAndConfirmed();

    Page<Alert> findByTunnelAndFirstAlertTimeBetweenOrderByCreatedAtDesc(
            String tunnel, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<Alert> findByFirstAlertTimeBetweenOrderByCreatedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<Alert> findBySensorTypeAndFirstAlertTimeBetweenOrderByCreatedAtDesc(
            String sensorType, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query("SELECT a.tunnel, COUNT(a) FROM Alert a WHERE a.firstAlertTime BETWEEN :startTime AND :endTime GROUP BY a.tunnel")
    List<Object[]> countByTunnelBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT a.level, COUNT(a) FROM Alert a WHERE a.firstAlertTime BETWEEN :startTime AND :endTime GROUP BY a.level")
    List<Object[]> countByLevelBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT a.sensorType, COUNT(a) FROM Alert a WHERE a.firstAlertTime BETWEEN :startTime AND :endTime GROUP BY a.sensorType")
    List<Object[]> countBySensorTypeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT FUNCTION('DATE', a.firstAlertTime), COUNT(a) FROM Alert a WHERE a.firstAlertTime BETWEEN :startTime AND :endTime GROUP BY FUNCTION('DATE', a.firstAlertTime) ORDER BY FUNCTION('DATE', a.firstAlertTime)")
    List<Object[]> countByDayBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Modifying
    @Query("UPDATE Alert a SET a.escalationLevel = :escalationLevel, a.escalationTime = :escalationTime " +
           "WHERE a.alertNo = :alertNo")
    int updateEscalationLevel(@Param("alertNo") String alertNo,
                              @Param("escalationLevel") String escalationLevel,
                              @Param("escalationTime") LocalDateTime escalationTime);

    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.confirmedBy = :confirmedBy, a.confirmedAt = :confirmedAt " +
           "WHERE a.alertNo = :alertNo")
    int confirmAlert(@Param("alertNo") String alertNo,
                     @Param("status") Integer status,
                     @Param("confirmedBy") String confirmedBy,
                     @Param("confirmedAt") LocalDateTime confirmedAt);

    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.processingBy = :processingBy, a.processingAt = :processingAt " +
           "WHERE a.alertNo = :alertNo")
    int processingAlert(@Param("alertNo") String alertNo,
                        @Param("status") Integer status,
                        @Param("processingBy") String processingBy,
                        @Param("processingAt") LocalDateTime processingAt);

    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.recoveredAt = :recoveredAt " +
           "WHERE a.alertNo = :alertNo")
    int recoverAlert(@Param("alertNo") String alertNo,
                     @Param("status") Integer status,
                     @Param("recoveredAt") LocalDateTime recoveredAt);

    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.closedBy = :closedBy, a.closedAt = :closedAt " +
           "WHERE a.alertNo = :alertNo")
    int closeAlert(@Param("alertNo") String alertNo,
                   @Param("status") Integer status,
                   @Param("closedBy") String closedBy,
                   @Param("closedAt") LocalDateTime closedAt);
}
