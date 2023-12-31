package com.lkd.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lkd.entity.TaskEntity;
import com.lkd.entity.TaskStatusTypeEntity;
import com.lkd.exception.LogicException;
import com.lkd.http.vo.CancelTaskViewModel;
import com.lkd.http.vo.TaskCollectVO;
import com.lkd.http.vo.TaskReportInfoVO;
import com.lkd.http.vo.TaskViewModel;
import com.lkd.vo.Pager;
import com.lkd.vo.UserWorkVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单业务逻辑
 */
public interface TaskService extends IService<TaskEntity> {


    /**
     * 通过条件搜索工单列表
     * @param pageIndex
     * @param pageSize
     * @param innerCode
     * @param userId
     * @param taskCode
     * @param isRepair 是否是运维工单
     * @return
     */
    Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end);





    /**
     * 获取所有状态类型
     * @return
     */
    List<TaskStatusTypeEntity> getAllStatus();

    /**
     * 创建工单
     * @param taskViewModel
     * @return
     */
    boolean create(TaskViewModel taskViewModel);

    /**
     * 接受工单
     * @param taskId 工单id
     * @param userId 当前登录人id
     * @return
     */
    Boolean acceptTask(Long taskId,Integer userId);

    /**
     * 取消工单
     * @param taskId 工单id
     * @param cancelTaskViewModel 描述
     * @return
     */
    Boolean cancelTask(long taskId, CancelTaskViewModel cancelTaskViewModel);

    /**
     * 完成工单
     * @param taskId 工单id
     * @param userId 接受人id
     * @return
     */
    Boolean complete(long taskId, Integer userId);

    /**
     * 获取最少工单用户
     * @param region 区域id
     * @Parm isSupply ture 运营工单 false 运维工单
     * @return 用户id if get nothing,return null
     */
    Integer getLeastUser(Long region,Boolean isSupply);

    /**
     * 获取工单的统计情况
     * @return
     */
    List<TaskReportInfoVO> getTaskReportInfo(LocalDateTime start, LocalDateTime end);

    /**
     * 获取排名前10的工作量
     * @param start
     * @param end
     * @return
     */
    List<UserWorkVO> getUserWorkTop10(LocalDate start, LocalDate end, Boolean isRepair, Long regionId);

    /**
     * 获取工单报表
     * @param start
     * @param end
     * @return
     */
    List<TaskCollectVO> getTaskReport(LocalDate start, LocalDate end);

    /**
     * 获取用户工作量详情
     * @param userId
     * @param start
     * @param end
     * @return
     */
    UserWorkVO getUserWork(Integer userId, LocalDateTime start, LocalDateTime end);

}
