package com.lkd.service;

import com.lkd.vo.BarCharVO;
import com.lkd.vo.OrderVO;
import com.lkd.vo.Pager;

import java.time.LocalDate;
import java.util.List;

/**
 * ES 订单库业务逻辑接口
 */
public interface ESOrderService {

    /**
     * 查询订单
     * @param pageIndex
     * @param pageSize
     * @return
     */
    Pager<OrderVO> search(Integer pageIndex, Integer pageSize, String orderNo, String openId, String startDate, String endDate);


    /**
     * 获取商圈下销量最好的前10商品
     * @return
     */
    List<Long> getTop10Sku(Integer businessId);


    /**
     * 获取地区销量统计
     * @param start
     * @param end
     * @return
     */
    BarCharVO getCollectByRegion(LocalDate start, LocalDate end);
}
