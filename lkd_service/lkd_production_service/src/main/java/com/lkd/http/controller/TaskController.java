package com.lkd.http.controller;
import com.lkd.entity.TaskDetailsEntity;
import com.lkd.entity.TaskEntity;
import com.lkd.entity.TaskStatusTypeEntity;
import com.lkd.entity.TaskTypeEntity;
import com.lkd.exception.LogicException;
import com.lkd.http.vo.*;
import com.lkd.service.*;
import com.lkd.vo.Pager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/task")
public class TaskController extends  BaseController{
    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskDetailsService taskDetailsService;

    @Autowired
    private TaskTypeService taskTypeService;



    /**
     * 搜索工单
     * @param pageIndex
     * @param pageSize
     * @param innerCode 设备编号
     * @param userId  工单所属人Id
     * @param taskCode 工单编号
     * @param status 工单状态
     * @param isRepair 是否是维修工单
     * @return
     */
    @GetMapping("/search")
    public Pager<TaskEntity> search(
            @RequestParam(value = "pageIndex",required = false,defaultValue = "1") Long pageIndex,
            @RequestParam(value = "pageSize",required = false,defaultValue = "10") Long pageSize,
            @RequestParam(value = "innerCode",required = false,defaultValue = "") String innerCode,
            @RequestParam(value = "userId",required = false,defaultValue = "") Integer userId,
            @RequestParam(value = "taskCode",required = false,defaultValue = "") String taskCode,
            @RequestParam(value = "status",required = false,defaultValue = "") Integer status,
            @RequestParam(value = "isRepair",required = false,defaultValue = "") Boolean isRepair,
            @RequestParam(value = "start",required = false,defaultValue = "") String start,
            @RequestParam(value = "end",required = false,defaultValue = "") String end){
        return taskService.search(pageIndex,pageSize,innerCode,userId,taskCode,status,isRepair,start,end);
    }



    /**
     * 根据taskId查询
     * @param taskId
     * @return 实体
     */
    @GetMapping("/taskInfo/{taskId}")
    public TaskEntity findById(@PathVariable Long taskId){
        return taskService.getById(taskId);
    }


    @GetMapping("/allTaskStatus")
    public List<TaskStatusTypeEntity> getAllStatus(){
        return taskService.getAllStatus();
    }

    /**
     * 获取工单类型
     * @return
     */
    @GetMapping("/typeList")
    public List<TaskTypeEntity> getProductionTypeList(){
        return taskTypeService.list();
    }

    /**
     * 获取工单详情
     * @param taskId
     * @return
     */
    @GetMapping("/details/{taskId}")
    public List<TaskDetailsEntity> getDetail(@PathVariable long taskId){
        return taskDetailsService.getByTaskId(taskId);
    }


}