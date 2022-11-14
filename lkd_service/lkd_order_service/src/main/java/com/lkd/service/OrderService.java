package com.lkd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lkd.vo.PayVO;
import com.lkd.entity.OrderEntity;
import com.lkd.vo.OrderVO;
import com.lkd.vo.Pager;

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


}
