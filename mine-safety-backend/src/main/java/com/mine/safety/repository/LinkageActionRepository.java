package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.LinkageAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LinkageActionRepository extends BaseMapper<LinkageAction> {

    @Select("SELECT a.* FROM linkage_actions a JOIN alert_rule_action_relations r ON a.id = r.action_id WHERE r.rule_id = #{ruleId} AND a.enabled = true ORDER BY r.execution_order ASC")
    List<LinkageAction> findActionsByRuleId(@Param("ruleId") Long ruleId);

    @Select("SELECT a.* FROM linkage_actions a JOIN alert_rule_action_relations r ON a.id = r.action_id WHERE r.rule_code = #{ruleCode} AND a.enabled = true ORDER BY r.execution_order ASC")
    List<LinkageAction> findActionsByRuleCode(@Param("ruleCode") String ruleCode);
}
