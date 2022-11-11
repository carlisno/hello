package com.lkd.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Strings;
import com.lkd.common.VMSystem;
import com.lkd.dao.TaskDao;
import com.lkd.entity.TaskDetailsEntity;
import com.lkd.entity.TaskEntity;
import com.lkd.entity.TaskStatusTypeEntity;
import com.lkd.exception.LogicException;
import com.lkd.feign.UserService;
import com.lkd.feign.VMService;
import com.lkd.http.vo.TaskDetailsViewModel;
import com.lkd.http.vo.TaskViewModel;
import com.lkd.service.TaskDetailsService;
import com.lkd.service.TaskService;
import com.lkd.service.TaskStatusTypeService;
import com.lkd.vo.Pager;
import com.lkd.vo.UserVO;
import com.lkd.vo.VmVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskDao,TaskEntity> implements TaskService{

    @Autowired
    private TaskStatusTypeService statusTypeService;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Autowired
    private VMService vmService;

    @Autowired
    private UserService userService;

    @Autowired
    private TaskDetailsService taskDetailsService;


    @Override
    public Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end) {
        Page<TaskEntity> page = new Page<>(pageIndex,pageSize);
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        if(!Strings.isNullOrEmpty(innerCode)){
            qw.eq(TaskEntity::getInnerCode,innerCode);
        }
        if(userId != null && userId > 0){
            qw.eq(TaskEntity::getUserId,userId);
        }
        if(!Strings.isNullOrEmpty(taskCode)){
            qw.like(TaskEntity::getTaskCode,taskCode);
        }
        if(status != null && status > 0){
            qw.eq(TaskEntity::getTaskStatus,status);
        }
        if(isRepair != null){
            if(isRepair){
                qw.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            }else {
                qw.eq(TaskEntity::getProductTypeId,VMSystem.TASK_TYPE_SUPPLY);
            }
        }
        if(!Strings.isNullOrEmpty(start) && !Strings.isNullOrEmpty(end)){
            qw
                    .ge(TaskEntity::getCreateTime, LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE))
                    .le(TaskEntity::getCreateTime,LocalDate.parse(end,DateTimeFormatter.ISO_LOCAL_DATE));
        }
        //根据最后更新时间倒序排序
        qw.orderByDesc(TaskEntity::getUpdateTime);

        return Pager.build(this.page(page,qw));
    }



    @Override
    public List<TaskStatusTypeEntity> getAllStatus() {
        QueryWrapper<TaskStatusTypeEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .ge(TaskStatusTypeEntity::getStatusId,VMSystem.TASK_STATUS_CREATE);

        return statusTypeService.list(qw);
    }

    /**
     * 创建工单
     * @param taskViewModel
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(TaskViewModel taskViewModel) {
        //调用售货机微服务中的方法
        VmVO vmInfo = vmService.getVMInfo(taskViewModel.getInnerCode());
        //调用用户微服务中的方法
        UserVO user = userService.getUser(taskViewModel.getUserId());
        if (Objects.isNull(vmInfo) || Objects.isNull(user)){
            throw new LogicException("售货机/用户不存在");
        }

        //插入工单数据
        TaskEntity taskEntity = getEntity(taskViewModel, vmInfo, user);
        this.save(taskEntity);

        //2.如果是补货工单,需要插入工单详情表的数据
        if(taskViewModel.getProductType() == VMSystem.TASK_TYPE_SUPPLY){
            insertTaskDetailData(taskViewModel, taskEntity);
        }

        return Boolean.TRUE;
    }

    /**
     * 插入工单详情
     * @param taskViewModel 入参
     * @param taskEntity 工单表对象
     */
    private void insertTaskDetailData(TaskViewModel taskViewModel, TaskEntity taskEntity) {
        List<TaskDetailsViewModel> details = taskViewModel.getDetails();
        //第一种实现方式,遍历循环,进行插入操作
        details.stream().forEach(each->{
            TaskDetailsEntity taskDetailsEntity = new TaskDetailsEntity();
            BeanUtils.copyProperties(each,taskDetailsEntity);
            taskDetailsEntity.setTaskId(taskEntity.getTaskId());
            taskDetailsService.save(taskDetailsEntity);
        });

        //第二种实现方式,需要插入工单详情数据
        List<TaskDetailsEntity> collect = details.stream().map(each->{
            TaskDetailsEntity taskDetailsEntity = new TaskDetailsEntity();
            BeanUtils.copyProperties(each,taskDetailsEntity);
            taskDetailsEntity.setTaskId(taskEntity.getTaskId());
            return taskDetailsEntity;
        }).collect(Collectors.toList());
    }

    /**
     * 填充工单的属性
     * @param taskViewModel
     * @param vmInfo
     * @param user
     * @return
     */
    private TaskEntity getEntity(TaskViewModel taskViewModel, VmVO vmInfo, UserVO user) {
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setTaskCode(generateTaskCode());
        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_CREATE);
        taskEntity.setCreateType(taskViewModel.getCreateType());
        taskEntity.setInnerCode(taskViewModel.getInnerCode());
        taskEntity.setRegionId(vmInfo.getRegionId());
        taskEntity.setUserId(taskViewModel.getUserId());
        taskEntity.setUserName(user.getUserName());
        taskEntity.setDesc(taskViewModel.getDesc());
        taskEntity.setProductTypeId(taskViewModel.getProductType());
        //todo:  AssignorId  maybe  null
        taskEntity.setAssignorId(taskViewModel.getAssignorId());
        taskEntity.setAddr(vmInfo.getNodeAddr());
        return taskEntity;
    }


    /**
     * 生成工单编号
     * @return
     */
    private String generateTaskCode(){
        //日期+序号
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));  //日期字符串
        String key= "lkd.task.code."+date; //redis key
        Object obj = redisTemplate.opsForValue().get(key);
        if(obj==null){
            redisTemplate.opsForValue().set(key,1L, Duration.ofDays(1) );
            return date+"0001";
        }
        return date+  Strings.padStart( redisTemplate.opsForValue().increment(key,1).toString(),4,'0');
    }

}
