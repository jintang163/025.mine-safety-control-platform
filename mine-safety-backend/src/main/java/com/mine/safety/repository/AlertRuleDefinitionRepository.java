package com.mine.safety.repository;

import com.mine.safety.domain.AlertRuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleDefinitionRepository extends JpaRepository<AlertRuleDefinition, Long> {

    Optional<AlertRuleDefinition> findByRuleCode(String ruleCode);

    List<AlertRuleDefinition> findByEnabled(Boolean enabled);

    List<AlertRuleDefinition> findByRuleTypeAndEnabled(String ruleType, Boolean enabled);

    List<AlertRuleDefinition> findBySensorTypeAndEnabled(String sensorType, Boolean enabled);

    List<AlertRuleDefinition> findByZoneCodeAndEnabled(String zoneCode, Boolean enabled);

    @Query("SELECT r FROM AlertRuleDefinition r WHERE r.enabled = true " +
           "AND (r.sensorType = :sensorType OR r.sensorType IS NULL) " +
           "AND (r.sensorId = :sensorId OR r.sensorId IS NULL) " +
           "AND (r.zoneCode = :zoneCode OR r.zoneCode IS NULL) " +
           "ORDER BY CASE r.ruleType " +
           "    WHEN 'COMPOUND' THEN 1 " +
           "    WHEN 'TREND' THEN 2 " +
           "    WHEN 'SINGLE_THRESHOLD' THEN 3 " +
           "END, r.level DESC")
    List<AlertRuleDefinition> findMatchingRules(
            @Param("sensorType") String sensorType,
            @Param("sensorId") String sensorId,
            @Param("zoneCode") String zoneCode);

    @Query("SELECT r.ruleCode FROM AlertRuleDefinition r WHERE r.enabled = true")
    List<String> findAllEnabledRuleCodes();

    boolean existsByRuleCode(String ruleCode);
}
