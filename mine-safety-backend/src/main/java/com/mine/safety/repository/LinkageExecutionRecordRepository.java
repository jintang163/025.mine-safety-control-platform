package com.mine.safety.repository;

import com.mine.safety.domain.LinkageExecutionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LinkageExecutionRecordRepository extends JpaRepository<LinkageExecutionRecord, Long> {

    List<LinkageExecutionRecord> findByExecutionNo(String executionNo);

    List<LinkageExecutionRecord> findByAlertId(Long alertId);

    List<LinkageExecutionRecord> findByRuleId(Long ruleId);

    List<LinkageExecutionRecord> findByActionId(Long actionId);

    List<LinkageExecutionRecord> findByStatus(Integer status);

    Page<LinkageExecutionRecord> findByActionTypeAndCreatedAtBetween(
            String actionType, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query("SELECT r FROM LinkageExecutionRecord r WHERE r.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY r.createdAt DESC")
    Page<LinkageExecutionRecord> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query("SELECT r.status, COUNT(r) FROM LinkageExecutionRecord r " +
           "WHERE r.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY r.status")
    List<Object[]> countByStatusAndTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT r.actionType, COUNT(r), SUM(CASE WHEN r.status = 2 THEN 1 ELSE 0 END) " +
           "FROM LinkageExecutionRecord r " +
           "WHERE r.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY r.actionType")
    List<Object[]> getActionStatistics(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
