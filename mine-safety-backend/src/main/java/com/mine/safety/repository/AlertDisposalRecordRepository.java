package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.AlertDisposalRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertDisposalRecordRepository extends BaseMapper<AlertDisposalRecord> {
}
