package com.lkd.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lkd.entity.PolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyDao extends BaseMapper<PolicyEntity> {
}
