package com.lkd.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lkd.entity.OrderEntity;
import com.lkd.entity.OrderReportVo;
import com.lkd.vo.SkuRetVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {

    @Select("select IFNULL(sum(amount),0) as amount, DATE_FORMAT(create_time, '%Y-%m-%d') as dd from tb_order where DATE_FORMAT(create_time, '%Y-%m-%d') >= #{start} and DATE_FORMAT(create_time, '%Y-%m-%d')<= #{end} group by  DATE_FORMAT(create_time, '%Y-%m-%d') ")
    List<OrderReportVo> getAmountCollect(@Param("start") LocalDate start ,
                                         @Param("end")  LocalDate end);


    @Select("select IFNULL(sum(amount),0) as amount, DATE_FORMAT(create_time, '%Y-%m') as dd from tb_order where DATE_FORMAT(create_time, '%Y-%m') >= #{start} and DATE_FORMAT(create_time, '%Y-%m')<= #{end} group by  DATE_FORMAT(create_time, '%Y-%m') ")
    List<OrderReportVo> getAmountCollectByMonth(@Param("start") LocalDate start ,
                                                @Param("end")  LocalDate end);

    @Select("select count(*) as count, sku_name as skuName from tb_order where DATE_FORMAT(create_time, '%Y-%m-%d') >= #{start} and DATE_FORMAT(create_time, '%Y-%m-%d')<= #{end} group by sku_name order by COUNT DESC  limit 0,#{pageNum}")
    List<SkuRetVO> getSkuRetVO(@Param("pageNum") Integer pageNum,
                               @Param("start") LocalDate start ,
                               @Param("end")  LocalDate end);
}
