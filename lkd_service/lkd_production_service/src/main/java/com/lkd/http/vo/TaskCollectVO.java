package com.lkd.http.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 工单状态统计图VO
 */
@Data
public class TaskCollectVO {

    /**
     * 完成数
     */
    private Integer finishCount;
    /**
     * 进行中工单数
     */
    private Integer progressCount;
    /**
     * 取消的工单数
     */
    private Integer cancelCount;
    /**
     * 发生日期
     */
    private LocalDate collectDate;

}
