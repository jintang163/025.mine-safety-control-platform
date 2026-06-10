package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.TrendRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrendRuleRepository extends BaseMapper<TrendRule> {
}
