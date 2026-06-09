package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.RuleActionRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleActionRelationRepository extends BaseMapper<RuleActionRelation> {
}
