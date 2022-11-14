package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;
import com.lkd.config.ConsulConfig;
import com.lkd.config.TopicConfig;
import com.lkd.contract.OrderCheck;
import com.lkd.contract.VendoutContract;
import com.lkd.contract.VendoutData;
import com.lkd.dao.OrderDao;
import com.lkd.feign.UserService;
import com.lkd.vo.*;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.OrderEntity;
import com.lkd.exception.LogicException;
import com.lkd.feign.VMService;
import com.lkd.service.OrderService;
import com.lkd.utils.DistributedLock;
import com.lkd.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private VMService vmService;
    @Autowired
    private UserService userService;

    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        QueryWrapper<OrderEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(OrderEntity::getOrderNo,orderNo);
        return this.getOne(qw);
    }

    @Override
    public OrderEntity createOrder(PayVO payVO) {

        Boolean aBoolean = vmService.hasCapacity(payVO.getInnerCode(), Long.valueOf(payVO.getSkuId()));
        if (!aBoolean){
            throw new LogicException("当前商品无库存,请勿下单!");
        }
        OrderEntity orderEntity = fillOrderInfo(payVO);
        return orderEntity;
    }

    /**
     * 填充订单信息
     * @param payVO 前端支付对象
     * @return 订单对象
     */
    private OrderEntity fillOrderInfo(PayVO payVO) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(payVO.getInnerCode()+System.nanoTime());
        orderEntity.setInnerCode(payVO.getInnerCode());

        VmVO vmInfo = vmService.getVMInfo(payVO.getInnerCode());
        BeanUtils.copyProperties(vmInfo,orderEntity);
        orderEntity.setAddr(vmInfo.getNodeAddr());

        orderEntity.setSkuId(Long.valueOf(payVO.getSkuId()));
        SkuVO sku = vmService.getSku(payVO.getSkuId());
        BeanUtils.copyProperties(sku,orderEntity);

        orderEntity.setStatus(VMSystem.ORDER_STATUS_CREATE);
        orderEntity.setAmount(sku.getPrice());
        //微信支付类型
        orderEntity.setPayType("2");
        orderEntity.setPayStatus(VMSystem.PAY_STATUS_NOPAY);
        orderEntity.setOpenId(payVO.getOpenId());
        //合作商的分成金额,分成*商品价格/100
        PartnerVO partner = userService.getPartner(vmInfo.getOwnerId());
        //精度问题
        BigDecimal multiply = new BigDecimal(partner.getRatio()).multiply(new BigDecimal(sku.getPrice()));
        BigDecimal divide = multiply.divide(new BigDecimal(100), 0, RoundingMode.HALF_UP);
        orderEntity.setBill(divide.intValue());

        this.save(orderEntity);
        return orderEntity;
    }

}
