package com.mine.safety.repository;

import com.mine.safety.domain.ThresholdApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThresholdApprovalRepository extends JpaRepository<ThresholdApproval, Long> {

    Optional<ThresholdApproval> findByApprovalNo(String approvalNo);

    List<ThresholdApproval> findByStatusOrderByCreatedAtDesc(Integer status);

    Page<ThresholdApproval> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    List<ThresholdApproval> findBySensorIdOrderByCreatedAtDesc(String sensorId);

    Page<ThresholdApproval> findBySensorIdOrderByCreatedAtDesc(String sensorId, Pageable pageable);

    List<ThresholdApproval> findByApplicantOrderByCreatedAtDesc(String applicant);

    @Query("SELECT ta FROM ThresholdApproval ta WHERE ta.status = :status " +
           "AND ta.sensorId = :sensorId ORDER BY ta.createdAt DESC")
    List<ThresholdApproval> findPendingBySensorId(@Param("sensorId") String sensorId,
                                                  @Param("status") Integer status);

    @Query("SELECT COUNT(ta) FROM ThresholdApproval ta WHERE ta.status = :status")
    long countByStatus(@Param("status") Integer status);
}
