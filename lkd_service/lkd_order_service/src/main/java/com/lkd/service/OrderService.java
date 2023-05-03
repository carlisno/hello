package com.lkd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;
import com.lkd.vo.*;
import com.lkd.entity.OrderEntity;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;

public interface OrderService extends IService<OrderEntity> {


    /**
     * 通过订单编号获取订单实体
     * @param orderNo
     * @return
     */
    OrderEntity getByOrderNo(String orderNo);

    /**
     * 初始化订单
     * @param payVO 支付请求对象
     * @return 订单对象
     */
    OrderEntity createOrder(PayVO payVO);

    /**
     * 发送出货消息
     * @param orderEntity 订单对象
     */
    void vendOut(OrderEntity orderEntity);

    /**
     * 获取销售额统计
     * @param start
     * @param end
     * @return
     */
    BarCharVO getAmountCollect(String type, LocalDate start, LocalDate end) throws ParseException;

    List<SkuRetVO> getSkuTop(Integer num, LocalDate start, LocalDate end);

    /**
     * 订单搜索
     * @param pageIndex
     * @param pageSize
     * @param orderNo
     * @param openId
     * @param startDate
     * @param endDate
     * @return
     */
    Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String openId, String startDate, String endDate);
}
