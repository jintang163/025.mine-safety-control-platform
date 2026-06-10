package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.ReportRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportRecordRepository extends BaseMapper<ReportRecord> {
}
