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

    /**
     * 根据报警编号查询（业务主键）
     *
     * @param alertNo 报警编号
     * @return 报警实体（可能为空）
     */
    Optional<Alert> findByAlertNo(String alertNo);

    /**
     * 按状态分页查询报警，按创建时间倒序
     *
     * @param status   状态（0-未处理，1-处理中，2-已处理，3-已忽略）
     * @param pageable 分页参数
     * @return 报警分页结果
     */
    Page<Alert> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    /**
     * 按报警级别分页查询，按创建时间倒序
     *
     * @param level    报警级别（INFO/WARNING/ALERT/EMERGENCY）
     * @param pageable 分页参数
     * @return 报警分页结果
     */
    Page<Alert> findByLevelOrderByCreatedAtDesc(String level, Pageable pageable);

    /**
     * 按传感器ID分页查询，按创建时间倒序
     *
     * @param sensorId 传感器ID
     * @param pageable 分页参数
     * @return 报警分页结果
     */
    Page<Alert> findBySensorIdOrderByCreatedAtDesc(String sensorId, Pageable pageable);

    /**
     * 根据传感器、状态、级别和时间范围查询报警
     *
     * @param sensorId 传感器ID
     * @param status   状态
     * @param level    级别
     * @param startTime 开始时间
     * @return 报警列表
     */
    List<Alert> findBySensorIdAndStatusAndLevelAndFirstAlertTimeAfter(
            String sensorId, Integer status, String level, LocalDateTime startTime);

    /**
     * 查询活跃的报警（未处理或处理中的同规则报警）
     * 用于避免同一传感器的同一规则重复创建报警
     *
     * @param sensorId 传感器ID
     * @param ruleId   规则ID
     * @return 活跃的报警（如果存在）
     */
    @Query("SELECT a FROM Alert a WHERE a.sensorId = :sensorId AND a.ruleId = :ruleId " +
           "AND a.status IN (0, 1) ORDER BY a.firstAlertTime DESC LIMIT 1")
    Alert findActiveAlert(@Param("sensorId") String sensorId, @Param("ruleId") Long ruleId);

    /**
     * 查询传感器阈值触发的活跃报警（ruleId = 0）
     * 用于避免同一传感器的阈值报警重复创建
     *
     * @param sensorId 传感器ID
     * @param level    报警级别
     * @return 活跃的报警（如果存在）
     */
    @Query("SELECT a FROM Alert a WHERE a.sensorId = :sensorId AND a.ruleId = 0 " +
           "AND a.level = :level AND a.status IN (0, 1) ORDER BY a.firstAlertTime DESC LIMIT 1")
    Alert findActiveThresholdAlert(@Param("sensorId") String sensorId, @Param("level") String level);

    /**
     * 确认/处理报警
     * 更新报警状态、处理人、处理时间和备注
     *
     * @param alertNo             报警编号
     * @param status              新状态
     * @param acknowledgedBy      处理人
     * @param acknowledgedAt      处理时间
     * @param acknowledgedComment 处理备注
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE Alert a SET a.status = :status, a.acknowledgedBy = :acknowledgedBy, " +
           "a.acknowledgedAt = :acknowledgedAt, a.acknowledgedComment = :acknowledgedComment " +
           "WHERE a.alertNo = :alertNo")
    int acknowledgeAlert(@Param("alertNo") String alertNo,
                         @Param("status") Integer status,
                         @Param("acknowledgedBy") String acknowledgedBy,
                         @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
                         @Param("acknowledgedComment") String acknowledgedComment);

    /**
     * 统计指定状态的报警数量
     *
     * @param status 状态
     * @return 报警数量
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = :status")
    long countByStatus(@Param("status") Integer status);

    /**
     * 统计指定级别在指定时间之后的报警数量
     *
     * @param level 报警级别
     * @param time  开始时间
     * @return 报警数量
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.level = :level AND a.createdAt >= :time")
    long countByLevelAndTimeAfter(@Param("level") String level, @Param("time") LocalDateTime time);

    /**
     * 更新报警频率（最后报警时间+报警次数+1）
     * 当同一报警重复触发时调用
     *
     * @param alertId        报警ID
     * @param lastAlertTime  最后报警时间
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE Alert a SET a.lastAlertTime = :lastAlertTime, a.alertCount = a.alertCount + 1 " +
           "WHERE a.id = :alertId")
    int updateAlertFrequency(@Param("alertId") Long alertId, @Param("lastAlertTime") LocalDateTime lastAlertTime);

    /**
     * 查询最近10条报警记录
     * 用于首页展示
     *
     * @return 最近10条报警
     */
    List<Alert> findTop10ByOrderByCreatedAtDesc();
}
