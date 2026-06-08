package com.mine.safety.repository;

import com.mine.safety.domain.ThresholdAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ThresholdAuditRepository extends JpaRepository<ThresholdAudit, Long> {

    List<ThresholdAudit> findBySensorIdOrderByCreatedAtDesc(String sensorId);

    Page<ThresholdAudit> findBySensorIdOrderByCreatedAtDesc(String sensorId, Pageable pageable);

    List<ThresholdAudit> findBySensorIdAndThresholdTypeOrderByCreatedAtDesc(String sensorId, String thresholdType);

    @Query("SELECT ta FROM ThresholdAudit ta WHERE ta.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY ta.createdAt DESC")
    List<ThresholdAudit> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    @Query("SELECT ta FROM ThresholdAudit ta WHERE ta.sensorId = :sensorId " +
           "AND ta.createdAt BETWEEN :startTime AND :endTime ORDER BY ta.createdAt DESC")
    List<ThresholdAudit> findBySensorIdAndTimeRange(@Param("sensorId") String sensorId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);
}
