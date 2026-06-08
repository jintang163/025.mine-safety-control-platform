package com.mine.safety.repository;

import com.mine.safety.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 报警规则数据访问接口
 * 继承JpaRepository，提供报警规则的CRUD操作和自定义查询
 *
 * 主要功能：
 *   - 查询启用/禁用的规则
 *   - 按传感器类型查询规则
 *   - 按传感器ID查询规则
 *   - 查询匹配特定传感器的所有规则（核心方法）
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    /**
     * 根据启用状态查询规则
     *
     * @param enabled 是否启用（0-禁用，1-启用）
     * @return 规则列表
     */
    List<AlertRule> findByEnabled(Integer enabled);

    /**
     * 根据传感器类型和启用状态查询规则
     *
     * @param sensorType 传感器类型
     * @param enabled    是否启用
     * @return 规则列表
     */
    List<AlertRule> findBySensorTypeAndEnabled(String sensorType, Integer enabled);

    /**
     * 根据传感器ID和启用状态查询规则
     *
     * @param sensorId 传感器ID
     * @param enabled  是否启用
     * @return 规则列表
     */
    List<AlertRule> findBySensorIdAndEnabled(String sensorId, Integer enabled);

    /**
     * 查询匹配特定传感器的所有启用规则
     * 核心查询方法，匹配逻辑：
     *   - 规则必须启用
     *   - sensor_type匹配 或 规则sensor_type为空（全局规则）
     *   - sensor_id匹配 或 规则sensor_id为空（全局规则）
     *
     * 例如：
     *   规则sensor_type=GAS, sensor_id=null → 匹配所有GAS类型传感器
     *   规则sensor_type=null, sensor_id=GAS-001 → 仅匹配GAS-001
     *   规则sensor_type=null, sensor_id=null → 匹配所有传感器
     *
     * @param sensorType 传感器类型
     * @param sensorId   传感器ID
     * @return 匹配的规则列表
     */
    @Query("SELECT ar FROM AlertRule ar WHERE ar.enabled = 1 AND " +
           "(ar.sensorType = :sensorType OR ar.sensorType IS NULL) AND " +
           "(ar.sensorId = :sensorId OR ar.sensorId IS NULL)")
    List<AlertRule> findMatchingRules(@Param("sensorType") String sensorType,
                                      @Param("sensorId") String sensorId);
}
