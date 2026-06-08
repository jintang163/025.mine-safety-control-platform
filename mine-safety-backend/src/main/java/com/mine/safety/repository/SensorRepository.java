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

/**
 * 传感器数据访问接口
 * 继承JpaRepository，提供传感器的CRUD操作和自定义查询
 *
 * 主要功能：
 *   - 按传感器ID查询
 *   - 按类型/状态查询传感器列表
 *   - 更新传感器状态（在线/离线/故障）
 *   - 查询需要检查离线状态的传感器
 */
@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    /**
     * 根据传感器ID查询（业务主键）
     *
     * @param sensorId 传感器ID
     * @return 传感器实体（可能为空）
     */
    Optional<Sensor> findBySensorId(String sensorId);

    /**
     * 根据传感器类型查询
     *
     * @param type 传感器类型（GAS/DUST/CO等）
     * @return 传感器列表
     */
    List<Sensor> findByType(String type);

    /**
     * 根据状态查询
     *
     * @param status 状态（0-离线，1-在线，2-故障）
     * @return 传感器列表
     */
    List<Sensor> findByStatus(Integer status);

    /**
     * 根据类型和状态查询
     *
     * @param type   传感器类型
     * @param status 状态
     * @return 传感器列表
     */
    List<Sensor> findByTypeAndStatus(String type, Integer status);

    /**
     * 检查传感器ID是否存在
     *
     * @param sensorId 传感器ID
     * @return true-存在，false-不存在
     */
    boolean existsBySensorId(String sensorId);

    /**
     * 更新传感器状态和最后在线时间
     * 用于传感器上线/离线状态变更
     *
     * @param sensorId       传感器ID
     * @param status         新状态
     * @param lastOnlineTime 最后在线时间
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE Sensor s SET s.status = :status, s.lastOnlineTime = :lastOnlineTime WHERE s.sensorId = :sensorId")
    int updateSensorStatus(@Param("sensorId") String sensorId,
                           @Param("status") Integer status,
                           @Param("lastOnlineTime") LocalDateTime lastOnlineTime);

    /**
     * 查询需要检查离线状态的传感器
     * 找出状态为在线但超过指定时间未更新的传感器
     *
     * @param status  当前状态（在线）
     * @param timeout 超时时间（最后在线时间早于此时间视为可能离线）
     * @return 可能离线的传感器列表
     */
    @Query("SELECT s FROM Sensor s WHERE s.status = :status AND s.lastOnlineTime < :timeout")
    List<Sensor> findSensorsToCheckOffline(@Param("status") Integer status,
                                            @Param("timeout") LocalDateTime timeout);

    /**
     * 根据区域编码查询传感器列表
     *
     * @param zoneCode 区域编码
     * @return 传感器列表
     */
    List<Sensor> findByZoneCode(String zoneCode);

    /**
     * 根据区域编码和状态查询传感器列表
     *
     * @param zoneCode 区域编码
     * @param status   状态
     * @return 传感器列表
     */
    List<Sensor> findByZoneCodeAndStatus(String zoneCode, Integer status);
}
