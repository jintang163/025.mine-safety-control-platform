package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.ThresholdApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ThresholdApprovalRepository extends BaseMapper<ThresholdApproval> {

    @Select("SELECT COUNT(*) FROM threshold_approval WHERE status = #{status}")
    long countByStatus(@Param("status") Integer status);
}
