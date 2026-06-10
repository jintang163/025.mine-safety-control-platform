package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.DeviceFaultOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceFaultOrderRepository extends BaseMapper<DeviceFaultOrder> {
}
