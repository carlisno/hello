package com.lkd.service.impl;

import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;
import com.lkd.exception.LogicException;
import com.lkd.service.ESOrderService;
import com.lkd.utils.JsonUtil;
import com.lkd.vo.BarCharVO;
import com.lkd.vo.OrderVO;
import com.lkd.vo.Pager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ESOrderServiceImpl implements ESOrderService {



    @Autowired
    private RestHighLevelClient esClient;

    @Override
    public Pager<OrderVO> search(Integer pageIndex, Integer pageSize, String orderNo, String openId, String startDate, String endDate) {
        //先整一个总搜索条件构造器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //设定查询条件
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        if (StringUtils.isNotBlank(orderNo)) {
            boolQueryBuilder.must(QueryBuilders.termQuery("order_no", orderNo));
        }
        if (StringUtils.isNotBlank(openId)) {
            boolQueryBuilder.must(QueryBuilders.termQuery("open_id", openId));
        }
        if (StringUtils.isNotBlank(startDate)) {
            boolQueryBuilder.must(QueryBuilders.rangeQuery("create_time").gt(startDate).lt(endDate));
        }
        //设定分页条件
        sourceBuilder.from(pageIndex * pageSize);
        sourceBuilder.size(pageSize);
        //设定排序条件
        sourceBuilder.sort("create_time", SortOrder.DESC);
        //建立搜索请求对象
        SearchRequest searchRequest = new SearchRequest("order").source(sourceBuilder);
        //开始请求并处理响应
        try {
            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            List<OrderVO> orderVOList = Lists.newArrayList();
            //获取所有命中的订单并转换为订单VO对象
            SearchHits hits = response.getHits();
            for (SearchHit hit : hits) {
                String orderStr = hit.getSourceAsString();
                OrderVO orderVO = JsonUtil.getByJson(orderStr, OrderVO.class);
                orderVOList.add(orderVO);
            }
            //新建分页对象并返回
            Pager<OrderVO> pager = new Pager<>();
            pager.setPageIndex(pageIndex);
            pager.setPageSize(pageSize);
            pager.setTotalCount(hits.getTotalHits().value);
            pager.setCurrentPageRecords(orderVOList);
            return pager;
        } catch (IOException e) {
            e.printStackTrace();
            log.error("搜索出错");
            throw new LogicException("搜索出错");
        }
    }

    @Override
    public List<Long> getTop10Sku(Integer businessId) {
        SearchRequest searchRequest=new SearchRequest("order");
        SearchSourceBuilder sourceBuilder=new SearchSourceBuilder();
        //查询条件：最近三个月

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("update_time");
        rangeQueryBuilder.gte( LocalDateTime.now().plusMonths(-3).format(  DateTimeFormatter.ISO_DATE_TIME )  );
        rangeQueryBuilder.lte( LocalDateTime.now().format(  DateTimeFormatter.ISO_DATE_TIME )  );

        BoolQueryBuilder boolQueryBuilder=QueryBuilders.boolQuery();
        boolQueryBuilder.must( rangeQueryBuilder );

        boolQueryBuilder.must( QueryBuilders.termQuery("business_id",businessId) );
        sourceBuilder.query(boolQueryBuilder);

        AggregationBuilder orderAgg = AggregationBuilders.terms("sku").field("sku_id")
                .subAggregation(AggregationBuilders.count("count").field("sku_id"))
                .order(BucketOrder.aggregation("count", false))
                .size(10);

        sourceBuilder.aggregation(orderAgg);
        searchRequest.source(sourceBuilder);

        try {
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            Aggregations aggregations = searchResponse.getAggregations();
            if(aggregations==null ) return  Lists.newArrayList();

            var term = (Terms)aggregations.get("sku");
            var buckets = term.getBuckets();

            return buckets.stream().map(  b->   Long.valueOf( b.getKey().toString() ) ).collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return Lists.newArrayList();
        }
    }


    @Override
    public BarCharVO getCollectByRegion(LocalDate start, LocalDate end) {
        SearchRequest searchRequest = new SearchRequest("order");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //根据时间范围搜索
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("create_time").gte(start).lte(end));
        boolQueryBuilder.filter(QueryBuilders.termQuery("pay_status", VMSystem.PAY_STATUS_PAYED));
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(0);
        //根据区域名称分组
        AggregationBuilder regionAgg = AggregationBuilders
                .terms("region")
                .field("region_name")
                .subAggregation(AggregationBuilders.sum("amount_sum").field("amount"))
                .order(BucketOrder.aggregation("amount_sum",false))
                .size(30);
        sourceBuilder.aggregation(regionAgg);
        searchRequest.source(sourceBuilder);

        var results = new BarCharVO();
        try {
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            var aggregation = searchResponse.getAggregations();
            if(aggregation == null) return results;

            var term = (ParsedStringTerms)aggregation.get("region");
            var buckets = term.getBuckets();
            if(buckets.size() <= 0) return results;

            buckets.stream().forEach(b->{
                results.getXAxis().add(b.getKeyAsString());

                var sumAgg = (ParsedSum) b.getAggregations().get("amount_sum");
                Double value =sumAgg.getValue();
                results.getSeries().add( value.intValue());
            });

        } catch (IOException e) {
            log.error("根据区域汇总数据出错",e);
        }
        return results;
    }



}
