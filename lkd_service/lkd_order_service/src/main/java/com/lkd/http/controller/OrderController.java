package com.lkd.http.controller;
import com.github.wxpay.plus.WxPayParam;
import com.github.wxpay.plus.WxPayTemplate;
import com.lkd.entity.OrderEntity;
import com.lkd.service.OrderService;
import com.lkd.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WxPayTemplate wxPayTemplate;

    @PostMapping("weixinPay")
    public Map<String,String> weixinPay(@RequestBody PayVO payVO){
        OrderEntity order = orderService.createOrder(payVO);
        //调用微信接口
        WxPayParam payParam = new WxPayParam();
        payParam.setOpenid(payVO.getOpenId());
        payParam.setTotalFee(order.getAmount());
        payParam.setBody(order.getSkuName());
        payParam.setOutTradeNo(order.getOrderNo());
        return wxPayTemplate.requestPay(payParam);
    }

}
