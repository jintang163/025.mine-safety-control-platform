package com.mine.safety.repository;

import com.mine.safety.domain.AlertDisposalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertDisposalRecordRepository extends JpaRepository<AlertDisposalRecord, Long> {

    List<AlertDisposalRecord> findByAlertNoOrderByCreatedAtDesc(String alertNo);

    List<AlertDisposalRecord> findByAlertNoAndDisposalTypeOrderByCreatedAtDesc(String alertNo, String disposalType);
}
