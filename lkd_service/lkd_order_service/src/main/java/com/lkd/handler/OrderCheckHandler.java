package com.lkd.handler;

import com.lkd.business.MsgHandler;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.OrderCheck;
import com.lkd.emq.Topic;
import com.lkd.entity.OrderEntity;
import com.lkd.service.OrderService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * @Title: OrderCheckHandler
 * @Author YuanRL
 * @Package com.lkd.handler
 * @Date 2022/11/15 20:02
 * @description: 接收订单延迟消息,完成订单状态的变更
 */
@Component
@Topic(TopicConfig.ORDER_CHECK_TOPIC)
public class OrderCheckHandler implements MsgHandler {

    @Autowired
    private OrderService orderService;

    @Override
    public void process(String jsonMsg) throws IOException {
        //json转换成对象
        OrderCheck orderCheck = JsonUtil.getByJson(jsonMsg, OrderCheck.class);

        OrderEntity orderEntity = orderService.getByOrderNo(orderCheck.getOrderNo());

        //更改为失效状态
        if (Objects.equals(orderEntity.getStatus(), VMSystem.ORDER_STATUS_CREATE)){
            orderEntity.setStatus(VMSystem.ORDER_STATUS_INVALID);
            orderService.updateById(orderEntity);
        }

    }
}
