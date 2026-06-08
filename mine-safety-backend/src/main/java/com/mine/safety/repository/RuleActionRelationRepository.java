package com.mine.safety.repository;

import com.mine.safety.domain.RuleActionRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleActionRelationRepository extends JpaRepository<RuleActionRelation, Long> {

    List<RuleActionRelation> findByRuleIdOrderByExecutionOrderAsc(Long ruleId);

    List<RuleActionRelation> findByRuleCodeOrderByExecutionOrderAsc(String ruleCode);

    List<RuleActionRelation> findByActionId(Long actionId);

    List<RuleActionRelation> findByActionCode(String actionCode);

    void deleteByRuleId(Long ruleId);

    void deleteByActionId(Long actionId);

    boolean existsByRuleIdAndActionId(Long ruleId, Long actionId);
}
