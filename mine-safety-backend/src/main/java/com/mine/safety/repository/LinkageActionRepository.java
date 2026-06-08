package com.mine.safety.repository;

import com.mine.safety.domain.LinkageAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkageActionRepository extends JpaRepository<LinkageAction, Long> {

    Optional<LinkageAction> findByActionCode(String actionCode);

    List<LinkageAction> findByEnabled(Boolean enabled);

    List<LinkageAction> findByActionTypeAndEnabled(String actionType, Boolean enabled);

    List<LinkageAction> findByTargetTypeAndTargetCodeAndEnabled(String targetType, String targetCode, Boolean enabled);

    @Query("SELECT a FROM LinkageAction a JOIN RuleActionRelation r ON a.id = r.actionId " +
           "WHERE r.ruleId = :ruleId AND a.enabled = true " +
           "ORDER BY r.executionOrder ASC")
    List<LinkageAction> findActionsByRuleId(@Param("ruleId") Long ruleId);

    @Query("SELECT a FROM LinkageAction a JOIN RuleActionRelation r ON a.id = r.actionId " +
           "WHERE r.ruleCode = :ruleCode AND a.enabled = true " +
           "ORDER BY r.executionOrder ASC")
    List<LinkageAction> findActionsByRuleCode(@Param("ruleCode") String ruleCode);

    boolean existsByActionCode(String actionCode);
}
