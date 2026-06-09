package com.mine.safety.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mine.safety.domain.PlcDevice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlcDeviceRepository extends BaseMapper<PlcDevice> {
}
