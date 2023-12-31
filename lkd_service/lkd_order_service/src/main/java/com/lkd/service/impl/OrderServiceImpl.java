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
import com.lkd.entity.OrderReportVo;
import com.lkd.feign.UserService;
import com.lkd.utils.DateUtil;
import com.lkd.viewmodel.OrderViewModel;
import com.lkd.viewmodel.Pager;
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
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private VMService vmService;
    @Autowired
    private UserService userService;
    @Autowired
    private MqttProducer mqttProducer;
    @Autowired
    private ConsulConfig consulConfig;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderDao orderDao;

    @Autowired
    private RestHighLevelClient esClient;

    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        QueryWrapper<OrderEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(OrderEntity::getOrderNo, orderNo);
        return this.getOne(qw);
    }

    @Override
    public OrderEntity createOrder(PayVO payVO) {

        Boolean aBoolean = vmService.hasCapacity(payVO.getInnerCode(), Long.valueOf(payVO.getSkuId()));
        if (!aBoolean) {
            throw new LogicException("当前商品无库存,请勿下单!");
        }
        //加锁
        DistributedLock lock = new DistributedLock(
                consulConfig.getConsulRegisterHost(),
                consulConfig.getConsulRegisterPort());
        DistributedLock.LockContext lockContext = lock.getLock(payVO.getInnerCode() + "_" + payVO.getSkuId(), 60);

        if (!lockContext.isGetLock()) {
            throw new LogicException("机器出货中请稍后再试");
        }

        //存入redis后是为了释放锁
        redisTemplate.boundValueOps(
                VMSystem.VM_LOCK_KEY_PREF + payVO.getInnerCode() + "_" + payVO.getSkuId())
                .set(lockContext.getSession(), Duration.ofSeconds(60));
        //5min之后,如果用户没有支付,将订单状态改为无效状态
        //初始化订单
        OrderEntity orderEntity = fillOrderInfo(payVO);
        //发送延迟消息实现
        sendDelayedMsg(orderEntity);
        return orderEntity;
    }

    /**
     * 发送订单延迟消息
     * @param orderEntity 订单对象
     */
    private void sendDelayedMsg(OrderEntity orderEntity) {
        try {
            OrderCheck orderCheck = new OrderCheck();
            orderCheck.setInnerCode(orderEntity.getInnerCode());
            orderCheck.setOrderNo(orderEntity.getOrderNo());
            //设置过期时间为60s
            mqttProducer.send("$delayed/60/"+TopicConfig.ORDER_CHECK_TOPIC,2,orderCheck);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送出货消息
     *
     * @param orderEntity 订单对象
     */
    @Override
    public void vendOut(OrderEntity orderEntity) {

        try {
            VendoutContract vendoutContract = new VendoutContract();
            vendoutContract.setInnerCode(orderEntity.getInnerCode());
            VendoutData vendoutData = new VendoutData();
            vendoutData.setOrderNo(orderEntity.getOrderNo());
            vendoutData.setSkuId(orderEntity.getSkuId());
            vendoutContract.setVendoutData(vendoutData);
            mqttProducer.send(TopicConfig.VMS_VEND_OUT_TOPIC, 2, vendoutContract);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 填充订单信息
     *
     * @param payVO 前端支付对象
     * @return 订单对象
     */
    private OrderEntity fillOrderInfo(PayVO payVO) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(payVO.getInnerCode() + System.nanoTime());
        orderEntity.setInnerCode(payVO.getInnerCode());

        VmVO vmInfo = vmService.getVMInfo(payVO.getInnerCode());
        BeanUtils.copyProperties(vmInfo, orderEntity);
        orderEntity.setAddr(vmInfo.getNodeAddr());

        orderEntity.setSkuId(Long.valueOf(payVO.getSkuId()));
        SkuVO sku = vmService.getSku(payVO.getSkuId());
        BeanUtils.copyProperties(sku, orderEntity);

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

    @Override
    public BarCharVO getAmountCollect(String type, LocalDate start, LocalDate end) throws ParseException {
        List<OrderReportVo> list = null;
        List<String> monthDates = null;
        if("1".equals(type)){
            list = orderDao.getAmountCollect(start, end);
            monthDates = DateUtil.findDates(start.toString(), end.toString());
        }else if("2".equals(type)){
            list = orderDao.getAmountCollectByMonth(start, end);
            monthDates = DateUtil.findMonthDates(start.toString(), end.toString());
        }
        Map<String, Integer> finalAmountCollect = new HashMap<>();
        for (OrderReportVo orderReportVo : list) {
            finalAmountCollect.put(orderReportVo.getDd(),orderReportVo.getAmount());
        }
        var result = new BarCharVO();
        result.getXAxis().addAll(monthDates);
        for (String monthDate : monthDates) {
            if(finalAmountCollect.containsKey(monthDate)){
                result.getSeries().add(finalAmountCollect.get(monthDate));
            }else {
                result.getSeries().add(0);
            }
        }
        return result;
    }

    @Override
    public List<SkuRetVO> getSkuTop(Integer num, LocalDate start, LocalDate end) {
        List<SkuRetVO> skuRetVO = orderDao.getSkuRetVO(num, start, end);
        return skuRetVO;
    }
    @Override
    public com.lkd.viewmodel.Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo, String openId, String startDate, String endDate) {

        //1.封装查询条件

        SearchRequest searchRequest=new SearchRequest("order");
        SearchSourceBuilder sourceBuilder =new SearchSourceBuilder();

        BoolQueryBuilder boolQueryBuilder= QueryBuilders.boolQuery();
        //订单号查询
        if(!Strings.isNullOrEmpty(orderNo)){
            boolQueryBuilder.must(  QueryBuilders.termQuery("order_no",orderNo)  );
        }
        //根据openId查询
        if(!Strings.isNullOrEmpty(openId)){
            boolQueryBuilder.must(  QueryBuilders.termQuery("open_id",openId)  );
        }
        //时间范围查询
        if(!Strings.isNullOrEmpty( startDate ) &&  !Strings.isNullOrEmpty(endDate) ){
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("update_time");
            rangeQueryBuilder.gte(startDate );
            rangeQueryBuilder.lte(endDate);
        }

        sourceBuilder.from((pageIndex-1)* pageSize);
        sourceBuilder.size(pageSize);

        sourceBuilder.trackTotalHits(true);
        sourceBuilder.query(boolQueryBuilder);
        searchRequest.source(sourceBuilder);

        //2.封装查询结果

        try {
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();
            SearchHit[] searchHits = hits.getHits();

            List<OrderViewModel> orderList=Lists.newArrayList();

            for(SearchHit hit:searchHits){

                String hitResult = hit.getSourceAsString();
                OrderViewModel order=new OrderViewModel();

                JsonNode jsonNode = JsonUtil.getTreeNode(hitResult);
                order.setId(jsonNode.findPath("id").asLong());
                order.setStatus(jsonNode.findPath("status").asInt());
                order.setBill(jsonNode.findPath("bill").asInt());
                order.setOwnerId(jsonNode.findPath("owner_id").asInt());
                order.setPayType(jsonNode.findPath("pay_type").asText());
                order.setOrderNo(jsonNode.findPath("order_no").asText());
                order.setInnerCode(jsonNode.findPath("inner_code").asText());
                order.setSkuName(jsonNode.findPath("sku_name").asText());
                order.setSkuId(jsonNode.findPath("sku_id").asLong());
                order.setPayStatus(jsonNode.findPath("pay_status").asInt());
                order.setBusinessName(jsonNode.findPath("business_name").asText());
                order.setBusinessId(jsonNode.findPath("business_id").asInt());
                order.setRegionId(jsonNode.findPath("region_id").asLong());
                order.setRegionName(jsonNode.findPath("region_name").asText());
                order.setPrice(jsonNode.findPath("price").asInt());
                order.setAmount(jsonNode.findPath("amount").asInt());
                order.setAddr(jsonNode.findPath("addr").asText());
                order.setOpenId(jsonNode.findPath("open_id").asText());

                order.setCreateTime(  LocalDateTime.parse( jsonNode.findPath("create_time").asText(),DateTimeFormatter.ISO_DATE_TIME ) );
                order.setUpdateTime(  LocalDateTime.parse( jsonNode.findPath("update_time").asText(),DateTimeFormatter.ISO_DATE_TIME ));

                orderList.add(order);
            }

            com.lkd.viewmodel.Pager<OrderViewModel> pager=new com.lkd.viewmodel.Pager<>();
            pager.setCurrentPageRecords(orderList);
            pager.setTotalCount( hits.getTotalHits().value );
            pager.setPageSize( searchHits.length );
            pager.setPageIndex(pageIndex);
            return  pager;

        } catch (IOException e) {
            e.printStackTrace();
            return Pager.buildEmpty();

        }

    }
}
