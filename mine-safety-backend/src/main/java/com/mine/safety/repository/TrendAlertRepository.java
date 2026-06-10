package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.TrendAlert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrendAlertRepository extends BaseMapper<TrendAlert> {
}
