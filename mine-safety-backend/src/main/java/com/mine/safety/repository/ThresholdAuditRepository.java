package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.ThresholdAudit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ThresholdAuditRepository extends BaseMapper<ThresholdAudit> {
}
