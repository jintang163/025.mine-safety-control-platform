package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.LinkageExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LinkageExecutionRecordRepository extends BaseMapper<LinkageExecutionRecord> {

    @Select("SELECT status, COUNT(*) as cnt FROM linkage_execution_records WHERE created_at BETWEEN #{startTime} AND #{endTime} GROUP BY status")
    List<Object[]> countByStatusAndTimeRange(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    @Select("SELECT action_type, COUNT(*) as total, SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as success_count FROM linkage_execution_records WHERE created_at BETWEEN #{startTime} AND #{endTime} GROUP BY action_type")
    List<Object[]> getActionStatistics(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
}
