package com.lkd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lkd.entity.OrderCollectEntity;
import com.lkd.vo.BarCharVO;
import com.lkd.vo.Pager;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderCollectService extends IService<OrderCollectEntity> {
    /**
     *获取一定时间范围内的合作商的点位分成数据
     * @param start
     * @param end
     * @return
     */
    List<OrderCollectEntity> getOwnerCollectByDate(Integer ownerId,LocalDate start,LocalDate end);

    /**
     * 订单数统计
     * @param start
     * @param end
     * @return
     */
    int orderCount(LocalDateTime start, LocalDateTime end);

    /**
     * 订金额统计
     * @param start
     * @param end
     * @return
     */
    int orderAmount(LocalDateTime start, LocalDateTime end);
    /**
     * 分成金额统计
     * @param start
     * @param end
     * @return
     */
    int totalBill(LocalDateTime start, LocalDateTime end);

    /**
     * 获取合作商分账汇总信息
     * @param pageIndex
     * @param pageSize
     * @param name
     * @param start
     * @param end
     * @return
     */
    Pager<OrderCollectEntity> getPartnerCollect(Long pageIndex, Long pageSize, String name, LocalDate start, LocalDate end);

    /**
     * 获取一定日期内合作商的收益统计
     * @param partnerId
     * @param start
     * @param end
     * @return
     */
    BarCharVO getCollect(Integer partnerId, LocalDate start, LocalDate end);


    /**
     * 获取合作商前12条点位分账数据
     * @param partnerId
     * @return
     */
    List<OrderCollectEntity> getTop12(Integer partnerId);

    /**
     * 合作商点位分账搜索
     * @param partnerId
     * @param nodeName
     * @param start
     * @param end
     * @return
     */
    Pager<OrderCollectEntity> search(Long pageIndex,Long pageSize,Integer partnerId, String nodeName, LocalDate start, LocalDate end);

//    /**
//     * 获取某一公司在一定时间内的销售数据
//     * @param companyId
//     * @param start
//     * @param end
//     * @return
//     */
//    List<OrderCollectEntity> getCompanyTrend(int companyId,LocalDate start,LocalDate end);
//
//    /**
//     * 获取某一地区
//     * @param start
//     * @param end
//     * @return
//     */
//    List<OrderCollectEntity> getAreaCollectByData(int areaId,LocalDate start,LocalDate end);

//    /**
//     * 获取一定时间范围内的销量前15的商品
//     * @param start
//     * @param end
//     * @return
//     */
//    List<OrderEntity> getTop15Skus(LocalDateTime start, LocalDateTime end);

//    /**
//     * 获取一定时间范围之内的汇总信息
//     * @param start
//     * @param end
//     * @return
//     */
//    OrderCollectEntity getCollectInfo(LocalDate start, LocalDate end);
}
