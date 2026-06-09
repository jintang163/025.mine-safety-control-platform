package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertRepository extends BaseMapper<Alert> {

    @Select("SELECT * FROM alerts WHERE sensor_id = #{sensorId} AND rule_id = #{ruleId} AND status IN (0, 1, 4) ORDER BY first_alert_time DESC LIMIT 1")
    Alert findActiveAlert(@Param("sensorId") String sensorId, @Param("ruleId") Long ruleId);

    @Select("SELECT * FROM alerts WHERE sensor_id = #{sensorId} AND rule_id = 0 AND level = #{level} AND status IN (0, 1, 4) ORDER BY first_alert_time DESC LIMIT 1")
    Alert findActiveThresholdAlert(@Param("sensorId") String sensorId, @Param("level") String level);

    @Update("UPDATE alerts SET status = #{status}, acknowledged_by = #{acknowledgedBy}, acknowledged_at = #{acknowledgedAt}, acknowledged_comment = #{acknowledgedComment} WHERE alert_no = #{alertNo}")
    int acknowledgeAlert(@Param("alertNo") String alertNo,
                         @Param("status") Integer status,
                         @Param("acknowledgedBy") String acknowledgedBy,
                         @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
                         @Param("acknowledgedComment") String acknowledgedComment);

    @Select("SELECT COUNT(*) FROM alerts WHERE status = #{status}")
    long countByStatus(@Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM alerts WHERE level = #{level} AND created_at >= #{time}")
    long countByLevelAndTimeAfter(@Param("level") String level, @Param("time") LocalDateTime time);

    @Update("UPDATE alerts SET last_alert_time = #{lastAlertTime}, alert_count = alert_count + 1 WHERE id = #{alertId}")
    int updateAlertFrequency(@Param("alertId") Long alertId, @Param("lastAlertTime") LocalDateTime lastAlertTime);

    @Select("SELECT * FROM alerts WHERE status = 0 AND escalation_level = #{level} AND created_at <= #{thresholdTime}")
    List<Alert> findUnconfirmedBeforeTime(@Param("level") String level, @Param("thresholdTime") LocalDateTime thresholdTime);

    @Select("SELECT tunnel, COUNT(*) as cnt FROM alerts WHERE first_alert_time BETWEEN #{startTime} AND #{endTime} GROUP BY tunnel")
    List<Object[]> countByTunnelBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT level, COUNT(*) as cnt FROM alerts WHERE first_alert_time BETWEEN #{startTime} AND #{endTime} GROUP BY level")
    List<Object[]> countByLevelBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT sensor_type, COUNT(*) as cnt FROM alerts WHERE first_alert_time BETWEEN #{startTime} AND #{endTime} GROUP BY sensor_type")
    List<Object[]> countBySensorTypeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT DATE(first_alert_time) as alert_date, COUNT(*) as cnt FROM alerts WHERE first_alert_time BETWEEN #{startTime} AND #{endTime} GROUP BY DATE(first_alert_time) ORDER BY alert_date")
    List<Object[]> countByDayBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Update("UPDATE alerts SET escalation_level = #{escalationLevel}, escalation_time = #{escalationTime} WHERE alert_no = #{alertNo}")
    int updateEscalationLevel(@Param("alertNo") String alertNo,
                              @Param("escalationLevel") String escalationLevel,
                              @Param("escalationTime") LocalDateTime escalationTime);

    @Update("UPDATE alerts SET status = #{status}, confirmed_by = #{confirmedBy}, confirmed_at = #{confirmedAt} WHERE alert_no = #{alertNo}")
    int confirmAlert(@Param("alertNo") String alertNo,
                     @Param("status") Integer status,
                     @Param("confirmedBy") String confirmedBy,
                     @Param("confirmedAt") LocalDateTime confirmedAt);

    @Update("UPDATE alerts SET status = #{status}, processing_by = #{processingBy}, processing_at = #{processingAt} WHERE alert_no = #{alertNo}")
    int processingAlert(@Param("alertNo") String alertNo,
                        @Param("status") Integer status,
                        @Param("processingBy") String processingBy,
                        @Param("processingAt") LocalDateTime processingAt);

    @Update("UPDATE alerts SET status = #{status}, recovered_at = #{recoveredAt} WHERE alert_no = #{alertNo}")
    int recoverAlert(@Param("alertNo") String alertNo,
                     @Param("status") Integer status,
                     @Param("recoveredAt") LocalDateTime recoveredAt);

    @Update("UPDATE alerts SET status = #{status}, closed_by = #{closedBy}, closed_at = #{closedAt} WHERE alert_no = #{alertNo}")
    int closeAlert(@Param("alertNo") String alertNo,
                   @Param("status") Integer status,
                   @Param("closedBy") String closedBy,
                   @Param("closedAt") LocalDateTime closedAt);
}
