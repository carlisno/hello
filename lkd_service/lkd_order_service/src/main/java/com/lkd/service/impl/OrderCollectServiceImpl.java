package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Strings;
import com.lkd.dao.OrderCollectDao;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.service.OrderCollectService;
import com.lkd.vo.BarCharVO;
import com.lkd.vo.Pager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderCollectServiceImpl extends ServiceImpl<OrderCollectDao,OrderCollectEntity> implements OrderCollectService{
    @Override
    public List<OrderCollectEntity> getOwnerCollectByDate(Integer ownerId,LocalDate start,LocalDate end){
        QueryWrapper<OrderCollectEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(OrderCollectEntity::getOwnerId,ownerId)
                .ge(OrderCollectEntity::getDate,start)
                .le(OrderCollectEntity::getDate,end)
                .groupBy(OrderCollectEntity::getNodeName,OrderCollectEntity::getDate)
                .orderByDesc(OrderCollectEntity::getDate);

        return this.list(qw);
    }

    @Override
    public int orderCount(LocalDateTime start, LocalDateTime end) {
        var qw = new QueryWrapper<OrderCollectEntity>();
        qw.select("IFNULL(sum(order_count),0) as order_count" );
        qw.lambda()
           .ge(OrderCollectEntity::getDate,start)
           .le(OrderCollectEntity::getDate,end);
        List<OrderCollectEntity> list = this.list(qw);
        return list.get(0).getOrderCount();
    }

    @Override
    public int orderAmount(LocalDateTime start, LocalDateTime end) {
        var qw = new QueryWrapper<OrderCollectEntity>();
        qw.select("IFNULL(sum(order_total_money),0) as order_total_money" );
        qw.lambda()
                .ge(OrderCollectEntity::getDate,start)
                .le(OrderCollectEntity::getDate,end);
        List<OrderCollectEntity> list = this.list(qw);
        return list.get(0).getOrderTotalMoney();
    }

    @Override
    public int totalBill(LocalDateTime start, LocalDateTime end) {
        var qw = new QueryWrapper<OrderCollectEntity>();
        qw.select("IFNULL(sum(total_bill),0) as total_bill" );
        qw
                .lambda()
                .ge(OrderCollectEntity::getDate,start)
                .le(OrderCollectEntity::getDate,end);
        List<OrderCollectEntity> list = this.list(qw);
        return list.get(0).getTotalBill();
    }

    @Override
    public Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate start, LocalDate end) {
        Page<OrderCollectEntity> page = new Page<>(pageIndex,pageSize);
        var qw = new QueryWrapper<OrderCollectEntity>();
        qw.select(
                "IFNULL(sum(order_count),0) as order_count",
                "IFNULL(sum(total_bill),0) as total_bill",
                "IFNULL(sum(order_total_money),0) as order_total_money",
                "IFNULL(min(ratio),0) as ratio",
                "owner_name",
                "date"
        );
        if(!Strings.isNullOrEmpty(name)){
            qw.lambda().like(OrderCollectEntity::getOwnerName,name);
        }
        qw
                .lambda()
                .ge(OrderCollectEntity::getDate,start)
                .le(OrderCollectEntity::getDate,end)
                .groupBy(OrderCollectEntity::getOwnerName,OrderCollectEntity::getDate)
                .orderByDesc(OrderCollectEntity::getDate);

        return Pager.build(this.page(page,qw));
    }

    @Override
    public BarCharVO getCollect(Integer partnerId, LocalDate start, LocalDate end) {
        var qw = new QueryWrapper<OrderCollectEntity>();
        qw
                .select("IFNULL(sum(total_bill),0) as total_bill","date")
                .lambda()
                .ge(OrderCollectEntity::getDate,start)
                .le(OrderCollectEntity::getDate,end)
                .eq(OrderCollectEntity::getOwnerId,partnerId)
                .orderByDesc(OrderCollectEntity::getDate)
                .groupBy(OrderCollectEntity::getDate);
        var mapCollect = this
                .list(qw)
                .stream()
                .collect(Collectors.toMap(OrderCollectEntity::getDate,OrderCollectEntity::getTotalBill));
        var result = new BarCharVO();
        start.datesUntil(end.plusDays(1), Period.ofDays(1))
                .forEach(date->{
                    result.getXAxis().add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    if(mapCollect.containsKey(date)){
                        result.getSeries().add(mapCollect.get(date));
                    }else {
                        result.getSeries().add(0);
                    }
                });
        return result;
    }

    @Override
    public List<OrderCollectEntity> getTop12(Integer partnerId) {
        var qw = new LambdaQueryWrapper<OrderCollectEntity>();
        qw.select(OrderCollectEntity::getDate,OrderCollectEntity::getNodeName,OrderCollectEntity::getOrderCount,OrderCollectEntity::getTotalBill)
                .eq(OrderCollectEntity::getOwnerId,partnerId)
                .orderByDesc(OrderCollectEntity::getDate)
                .last("limit 12");

        return this.list(qw);
    }

    @Override
    public Pager<OrderCollectEntity> search(Long pageIndex,Long pageSize,Integer partnerId, String nodeName, LocalDate start, LocalDate end) {
        var qw = new LambdaQueryWrapper<OrderCollectEntity>();
        qw
                .select(OrderCollectEntity::getDate,OrderCollectEntity::getNodeName,OrderCollectEntity::getOrderCount,OrderCollectEntity::getTotalBill)
                .eq(OrderCollectEntity::getOwnerId,partnerId);
        if(!Strings.isNullOrEmpty(nodeName)){
            qw.like(OrderCollectEntity::getNodeName,nodeName);
        }
        if(start !=null && end != null){
            qw
                    .ge(OrderCollectEntity::getDate,start)
                    .le(OrderCollectEntity::getDate,end);
        }
        qw.orderByDesc(OrderCollectEntity::getDate);
        var page = new Page<OrderCollectEntity>(pageIndex,pageSize);
        return Pager.build(this.page(page,qw));
    }
}
