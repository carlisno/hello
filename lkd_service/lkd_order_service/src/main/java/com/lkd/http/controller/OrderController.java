package com.lkd.http.controller;
import com.github.wxpay.plus.WXConfig;
import com.github.wxpay.plus.WxPayParam;
import com.github.wxpay.plus.WxPayTemplate;
import com.lkd.common.VMSystem;
import com.lkd.entity.OrderEntity;
import com.lkd.service.ESOrderService;
import com.lkd.service.OrderService;
import com.lkd.vo.OrderVO;
import com.lkd.vo.Pager;
import com.lkd.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private WxPayTemplate wxPayTemplate;
    @Autowired
    private ESOrderService esOrderService;

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
    /**
     * 微信支付回调接口
     * @param request
     * @return
     */
    @RequestMapping("/payNotify")
    @ResponseBody
    public void payNotify(HttpServletRequest request, HttpServletResponse response){

        try {
            Map<String, String> result = wxPayTemplate.validPay(request.getInputStream());
            //返回码成功
            if("SUCCESS".equals( result.get("code") )){
                //获取订单号
                String orderSn= result.get("order_sn");
                log.info("支付成功，修改订单状态和支付状态，订单号：{}",orderSn);
                //1.修改订单状态和支付状态
                OrderEntity byOrderNo = orderService.getByOrderNo(orderSn);
                byOrderNo.setStatus(VMSystem.ORDER_STATUS_PAYED);
                byOrderNo.setPayStatus(VMSystem.PAY_STATUS_PAYED);
                orderService.updateById(byOrderNo);

                //2.通知售货机微服务进行库存的扣减
                orderService.vendOut(byOrderNo);

            }
            //给微信支付一个成功的响应
            response.setContentType("text/xml");
            response.getWriter().write(WXConfig.RESULT);
        }catch (Exception e){
            log.error("支付回调处理失败",e);
        }
    }
    /**
     * 订单查询
     * @param pageIndex
     * @param pageSize
     * @param orderNo
     * @param openId
     * @param startDate
     * @param endDate
     * @return
     */
    @GetMapping("/search")
    public Pager<OrderVO> search(
            @RequestParam(value = "pageIndex",required = false,defaultValue = "1") Integer pageIndex,
            @RequestParam(value = "pageSize",required = false,defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo",required = false,defaultValue = "") String orderNo,
            @RequestParam(value = "openId",required = false,defaultValue = "") String openId,
            @RequestParam(value = "startDate",required = false,defaultValue = "") String startDate,
            @RequestParam(value = "endDate",required = false,defaultValue = "") String endDate){
        return esOrderService.search(pageIndex,pageSize,orderNo,openId,startDate,endDate);
    }
}
