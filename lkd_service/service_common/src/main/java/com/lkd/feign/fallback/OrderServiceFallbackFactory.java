package com.lkd.feign.fallback;
import com.google.common.collect.Lists;
import com.lkd.feign.OrderService;
import com.lkd.feign.VMService;
import com.lkd.vo.PayVO;
import com.lkd.vo.SkuVO;
import com.lkd.vo.VmVO;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务熔断   服务降级
 */
@Component
@Slf4j
public class OrderServiceFallbackFactory implements FallbackFactory<OrderService> {


    @Override
    public OrderService create(Throwable throwable) {
        return new OrderService() {
            @Override
            public Map<String, String> weixinPay(PayVO payVO) {
                HashMap<String, String> resultMap = new HashMap<>();
                resultMap.put("code","RETURN_FAIL");
                resultMap.put("msg","当前网络忙,请稍后重试");
                return resultMap;
            }
        };
    }
}
