package com.mine.safety.repository;

import com.mine.safety.domain.AlertEscalationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertEscalationLogRepository extends JpaRepository<AlertEscalationLog, Long> {

    List<AlertEscalationLog> findByAlertNoOrderByCreatedAtDesc(String alertNo);

    List<AlertEscalationLog> findByToLevelOrderByCreatedAtDesc(String toLevel);
}
