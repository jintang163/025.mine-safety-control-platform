package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.ReportTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportTemplateRepository extends BaseMapper<ReportTemplate> {
}
