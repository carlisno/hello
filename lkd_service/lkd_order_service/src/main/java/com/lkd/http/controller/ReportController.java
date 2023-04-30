package com.lkd.http.controller;
import com.lkd.service.ESOrderService;
import com.lkd.service.OrderCollectService;
import com.lkd.service.OrderService;
import com.lkd.vo.BarCharVO;
import com.lkd.vo.SkuRetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表controller
 */
@RestController
@RequestMapping("/report")
public class ReportController {


    @Autowired
    private ESOrderService esOrderService;

    @Autowired
    OrderService orderService;

    @Autowired
    private OrderCollectService orderCollectService;



    /**
     * 订单数统计
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/orderCount")
    public int orderCount( @RequestParam(value = "start",required = true,defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                           @RequestParam(value = "end",required = true,defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end){
        return orderCollectService.orderCount(start,end);
    }

    /**
     * 订单金额统计
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/orderAmount")
    public int orderAmount(@RequestParam(value = "start",required = true,defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                           @RequestParam(value = "end",required = true,defaultValue = "") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end){
        return orderCollectService.orderAmount(start,end);
    }

    /**
     * 获取销售额统计
     * @param type
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/amountCollect/{type}/{start}/{end}")
    public BarCharVO getAmountCollect(
            @PathVariable String type,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) throws ParseException {
        return orderService.getAmountCollect(type,start,end);
    }

    /**
     * 根据地区汇总销售额数据
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/regionCollect/{start}/{end}")
    public BarCharVO getRegionCollect(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        return esOrderService.getCollectByRegion(start,end);
    }

    /**
     * 获取销售前几的商品
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/skuTop/{num}/{start}/{end}")
    public List<SkuRetVO> getSkuTop(
            @PathVariable Integer num,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        return orderService.getSkuTop(num, start,end);
    }
}
