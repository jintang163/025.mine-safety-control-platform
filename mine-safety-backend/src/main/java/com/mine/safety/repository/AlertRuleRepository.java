package com.mine.safety.repository;

import com.mine.safety.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabled(Integer enabled);

    List<AlertRule> findBySensorTypeAndEnabled(String sensorType, Integer enabled);

    List<AlertRule> findBySensorIdAndEnabled(String sensorId, Integer enabled);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.enabled = 1 AND " +
           "(ar.sensorType = :sensorType OR ar.sensorType IS NULL) AND " +
           "(ar.sensorId = :sensorId OR ar.sensorId IS NULL)")
    List<AlertRule> findMatchingRules(@Param("sensorType") String sensorType,
                                      @Param("sensorId") String sensorId);
}
