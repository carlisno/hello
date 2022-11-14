package com.lkd.feign;

import com.lkd.feign.fallback.OrderServiceFallbackFactory;
import com.lkd.feign.fallback.VmServiceFallbackFactory;
import com.lkd.vo.PayVO;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 订单微服务
 */
@FeignClient(value = "order-service",fallbackFactory = OrderServiceFallbackFactory.class)
public interface OrderService {

    /**
     * 初始化订单
     * @param payVO 前端请求对象
     * @return 微信预支付返回结果
     */
    @PostMapping("/order/weixinPay")
    Map<String,String> weixinPay(@RequestBody PayVO payVO);
}
