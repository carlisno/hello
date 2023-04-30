package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyChannel;
import com.lkd.contract.SupplyContract;
import com.lkd.contract.TaskCompleteContract;
import com.lkd.dao.TaskDao;
import com.lkd.emq.MqttProducer;
import com.lkd.entity.TaskDetailsEntity;
import com.lkd.entity.TaskEntity;
import com.lkd.entity.TaskStatusTypeEntity;
import com.lkd.exception.LogicException;
import com.lkd.feign.UserService;
import com.lkd.feign.VMService;
import com.lkd.http.vo.CancelTaskViewModel;
import com.lkd.http.vo.TaskDetailsViewModel;
import com.lkd.http.vo.TaskReportInfoVO;
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
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskServiceImpl extends ServiceImpl<TaskDao, TaskEntity> implements TaskService {

    @Autowired
    private TaskStatusTypeService statusTypeService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VMService vmService;

    @Autowired
    private UserService userService;

    @Autowired
    private TaskDetailsService taskDetailsService;

    @Autowired
    private MqttProducer mqttProducer;

    @Override
    public Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end) {
        Page<TaskEntity> page = new Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        if (!Strings.isNullOrEmpty(innerCode)) {
            qw.eq(TaskEntity::getInnerCode, innerCode);
        }
        if (userId != null && userId > 0) {
            qw.eq(TaskEntity::getUserId, userId);
        }
        if (!Strings.isNullOrEmpty(taskCode)) {
            qw.like(TaskEntity::getTaskCode, taskCode);
        }
        if (status != null && status > 0) {
            qw.eq(TaskEntity::getTaskStatus, status);
        }
        if (isRepair != null) {
            if (isRepair) {
                qw.ne(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            } else {
                qw.eq(TaskEntity::getProductTypeId, VMSystem.TASK_TYPE_SUPPLY);
            }
        }
        if (!Strings.isNullOrEmpty(start) && !Strings.isNullOrEmpty(end)) {
            qw
                    .ge(TaskEntity::getCreateTime, LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE))
                    .le(TaskEntity::getCreateTime, LocalDate.parse(end, DateTimeFormatter.ISO_LOCAL_DATE));
        }
        //根据最后更新时间倒序排序
        qw.orderByDesc(TaskEntity::getUpdateTime);

        return Pager.build(this.page(page, qw));
    }


    @Override
    public List<TaskStatusTypeEntity> getAllStatus() {
        QueryWrapper<TaskStatusTypeEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .ge(TaskStatusTypeEntity::getStatusId, VMSystem.TASK_STATUS_CREATE);

        return statusTypeService.list(qw);
    }

    /**
     * 创建工单
     *
     * @param taskViewModel
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(TaskViewModel taskViewModel) {
        //1.填充工单数据
        //调用售货机微服务中的方法
        VmVO vmInfo = vmService.getVMInfo(taskViewModel.getInnerCode());
        //调用用户微服务中的方法
        UserVO user = userService.getUser(taskViewModel.getUserId());
        if (Objects.isNull(vmInfo) || Objects.isNull(user)) {
            throw new LogicException("售货机/用户不存在");
        }

        //2.检验售货机的状态
        checkVmStatus(taskViewModel, vmInfo);

        //3.校验同一台售货机是否有未完成的桶类型的工单
        if (getEntityQueryWrapper(taskViewModel)) {
            throw new LogicException("同一台售货机下,存在未完成的售货机工单");
        }

        //插入工单数据
        TaskEntity taskEntity = getEntity(taskViewModel, vmInfo, user);
        this.save(taskEntity);

        //4.如果是补货工单,需要插入工单详情表的数据
        if (taskViewModel.getProductType() == VMSystem.TASK_TYPE_SUPPLY) {
            insertTaskDetailData(taskViewModel, taskEntity);
        }

        //5.把用户人(执行人)的分数加1
        updateZSetScore(user, 1);
        return Boolean.TRUE;
    }

    /**
     * 接受工单
     *
     * @param taskId 工单id
     * @param userId 当前登录人id
     * @return
     */
    @Override
    public Boolean acceptTask(Long taskId, Integer userId) {
        //更改工单状态
        TaskEntity byId = getById(taskId);
        if (!byId.getTaskStatus().equals(VMSystem.TASK_STATUS_CREATE)) {
            throw new LogicException("当前工单不是待处理状态,不能接受");
        }
        if (!Objects.equals(userId, byId.getUserId())) {
            throw new LogicException("当前工单的执行人不是您,请勿再次操作");
        }
        byId.setTaskStatus(VMSystem.TASK_STATUS_PROGRESS);

        updateById(byId);
        return Boolean.TRUE;
    }

    /**
     * 取消工单
     *
     * @param taskId              工单id
     * @param cancelTaskViewModel 描述
     * @return
     */
    @Override
    public Boolean cancelTask(long taskId, CancelTaskViewModel cancelTaskViewModel) {
        TaskEntity taskEntity = getById(taskId);
        if (taskEntity.getTaskStatus().equals(VMSystem.TASK_STATUS_CANCEL)
                || taskEntity.getTaskStatus().equals(VMSystem.TASK_STATUS_FINISH)) {
            throw new LogicException("当前工单已经结束,请勿再此操作");
        }
        if (!cancelTaskViewModel.getUserId().equals(taskEntity.getUserId())
                && !cancelTaskViewModel.getUserId().equals(taskEntity.getAssignorId())) {
            throw new LogicException("当前工单已经结束,请勿再此操作");
        }

        taskEntity.setTaskStatus(VMSystem.TASK_STATUS_CANCEL);
        taskEntity.setDesc(cancelTaskViewModel.getDesc());

        updateById(taskEntity);

        //把用户人(执行人)的分数加1
        UserVO user = userService.getUser(taskEntity.getUserId());
        updateZSetScore(user, -1);
        return Boolean.TRUE;
    }

    /**
     * 更新分数
     *
     * @param user  用户信息
     * @param score 操作的分数
     */
    private void updateZSetScore(UserVO user, Integer score) {
        String redisKey = VMSystem.REGION_TASK_KEY_PREF
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "." + user.getRegionId()
                + "." + user.getRoleCode();
        redisTemplate.opsForZSet().incrementScore(redisKey, user.getUserId(), score);
    }

    /**
     * 完成工单
     *
     * @param taskId 工单id
     * @param userId 接受人id
     * @return
     */
    @Override
    public Boolean complete(long taskId, Integer userId) {
        //更改工单状态
        TaskEntity byId = getById(taskId);
        if (!byId.getTaskStatus().equals(VMSystem.TASK_STATUS_PROGRESS)) {
            throw new LogicException("当前工单不是进行中的状态,不能接受");
        }
        if (!Objects.equals(userId, byId.getUserId())) {
            throw new LogicException("当前工单的执行人不是您,请勿再次操作");
        }
        byId.setTaskStatus(VMSystem.TASK_STATUS_FINISH);

        updateById(byId);
        //发送运维工单完成消息
        if (byId.getProductTypeId().equals(VMSystem.TASK_TYPE_DEPLOY)
                || byId.getProductTypeId().equals(VMSystem.TASK_TYPE_REVOKE)) {
            sendCompleteOpsMsg(byId);
        }

        //完成补货工单消息发送
        if (Objects.equals(byId.getProductTypeId(), VMSystem.TASK_TYPE_SUPPLY)) {
            sendCompleteSupplyTaskMsg(taskId, byId);
        }

        return Boolean.TRUE;
    }

    /**
     * 获取最少工单用户
     *
     * @param region 区域id
     * @return 用户id  get nothing,return null
     * @Parm isSupply ture 运营工单 false 运维工单
     */
    @Override
    public Integer getLeastUser(Long region, Boolean isSupply) {
        String roleCode = VMSystem.USER_OPS_ROLE;
        if (!isSupply) {
            roleCode = VMSystem.USER_SUPPLY_ROLE;
        }

        String redisKey = VMSystem.REGION_TASK_KEY_PREF
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "." + region
                + "." + roleCode;
        //范围
        Set<Object> range = redisTemplate.opsForZSet().range(redisKey, 0, 0);
        if (CollectionUtils.isEmpty(range)) {
            return null;
        }
        return (Integer) new ArrayList<>(range).get(0);
    }

    /**
     * 发送补货数据
     *
     * @param taskId 工单Id
     * @param byId   工单对象
     */
    private void sendCompleteSupplyTaskMsg(long taskId, TaskEntity byId) {
        try {
            List<TaskDetailsEntity> taskDetailsEntities = taskDetailsService.getByTaskId(taskId);
            List<SupplyChannel> collect = taskDetailsEntities.stream().map(each -> {
                SupplyChannel supplyChannel = new SupplyChannel();
                supplyChannel.setChannelId(each.getChannelCode());
                supplyChannel.setCapacity(each.getExpectCapacity());
                return supplyChannel;
            }).collect(Collectors.toList());

            SupplyContract supplyContract = new SupplyContract();
            supplyContract.setSupplyData(collect);
            supplyContract.setInnerCode(byId.getInnerCode());
            mqttProducer.send(TopicConfig.VMS_SUPPLY_TOPIC, 2, supplyContract);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送运维工单完成消息
     *
     * @param byId
     */
    private void sendCompleteOpsMsg(TaskEntity byId) {
        try {
            TaskCompleteContract taskCompleteContract = new TaskCompleteContract();
            taskCompleteContract.setInnerCode(byId.getInnerCode());
            taskCompleteContract.setTaskType(byId.getProductTypeId());
            mqttProducer.send(TopicConfig.VMS_COMPLETED_TOPIC, 2, taskCompleteContract);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param taskViewModel
     * @return
     */
    private Boolean getEntityQueryWrapper(TaskViewModel taskViewModel) {
        QueryWrapper<TaskEntity> taskEntityQueryWrapper = new QueryWrapper<>();
        taskEntityQueryWrapper.lambda().select(TaskEntity::getTaskId)
                .eq(TaskEntity::getInnerCode, taskViewModel.getInnerCode())
                .eq(TaskEntity::getProductTypeId, taskViewModel.getProductType())
                .lt(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_PROGRESS);
        return this.count(taskEntityQueryWrapper) > 0;
    }

    /**
     * 校验售货机状态
     *
     * @param taskViewModel 工单创建入参
     * @param vmInfo        售货机信息
     */
    private void checkVmStatus(TaskViewModel taskViewModel, VmVO vmInfo) {
        if (taskViewModel.getProductType() == VMSystem.TASK_TYPE_DEPLOY
                && vmInfo.getVmStatus().equals(VMSystem.VM_STATUS_RUNNING)
        ) {
            throw new LogicException("当前售货机已经是运营状态,请勿投放");
        }
        if (taskViewModel.getProductType() == VMSystem.TASK_TYPE_SUPPLY
                && !vmInfo.getVmStatus().equals(VMSystem.VM_STATUS_RUNNING)
        ) {
            throw new LogicException("当前售货机不是运营状态,请勿投放");
        }
        if (taskViewModel.getProductType() == VMSystem.TASK_TYPE_REVOKE
                && !vmInfo.getVmStatus().equals(VMSystem.VM_STATUS_RUNNING)
        ) {
            throw new LogicException("当前售货机不是运营状态,请勿撤机");
        }
    }

    /**
     * 插入工单详情
     *
     * @param taskViewModel 入参
     * @param taskEntity    工单表对象
     */
    private void insertTaskDetailData(TaskViewModel taskViewModel, TaskEntity taskEntity) {
        List<TaskDetailsViewModel> details = taskViewModel.getDetails();
        //第一种实现方式,遍历循环,进行插入操作
        details.stream().forEach(each -> {
            TaskDetailsEntity taskDetailsEntity = new TaskDetailsEntity();
            BeanUtils.copyProperties(each, taskDetailsEntity);
            taskDetailsEntity.setTaskId(taskEntity.getTaskId());
            taskDetailsService.save(taskDetailsEntity);
        });
//        //第二种实现方式,需要插入工单详情数据
//        List<TaskDetailsEntity> collect = details.stream().map(each->{
//            TaskDetailsEntity taskDetailsEntity = new TaskDetailsEntity();
//            BeanUtils.copyProperties(each,taskDetailsEntity);
//            taskDetailsEntity.setTaskId(taskEntity.getTaskId());
//            return taskDetailsEntity;
//        }).collect(Collectors.toList());
    }

    /**
     * 填充工单的属性
     *
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
     *
     * @return
     */
    private String generateTaskCode() {
        //日期+序号
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));  //日期字符串
        String key = "lkd.task.code." + date; //redis key
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            redisTemplate.opsForValue().set(key, 1L, Duration.ofDays(1));
            return date + "0001";
        }
        return date + Strings.padStart(redisTemplate.opsForValue().increment(key, 1).toString(), 4, '0');
    }

    @Override
    public List<TaskReportInfoVO> getTaskReportInfo(LocalDateTime start, LocalDateTime end) {

        //运营工单总数total
        var supplyTotal = this.taskCount(start, end, false, null);
        //运维工单总数
        var repairTotal = this.taskCount(start, end, true, null);
        //完成的运营工单总数
        var completedSupply = this.taskCount(start, end, false, VMSystem.TASK_STATUS_FINISH);
        //完成的运维工单总数
        var completedRepair = this.taskCount(start, end, true, VMSystem.TASK_STATUS_FINISH);
        //拒绝掉的运营工单总数
        var cancelSupply = this.taskCount(start, end, false, VMSystem.TASK_STATUS_CANCEL);
        //拒绝掉的运维工单总数
        var cancelRepair = this.taskCount(start, end, true, VMSystem.TASK_STATUS_CANCEL);
        // 获取运营人员数量
        var operatorCount = userService.getOperatorCount();
        //获取运维人员总数
        var repairerCount = userService.getRepairerCount();

        List<TaskReportInfoVO> result = Lists.newArrayList();

        //运营人员
        var supplyTaskInfo = new TaskReportInfoVO();
        supplyTaskInfo.setTotal(supplyTotal);
        supplyTaskInfo.setCancelTotal(cancelSupply);
        supplyTaskInfo.setCompletedTotal(completedSupply);
        supplyTaskInfo.setRepair(false);
        supplyTaskInfo.setWorkerCount(operatorCount);
        result.add(supplyTaskInfo);

        //运维人员
        var repairTaskInfo = new TaskReportInfoVO();
        repairTaskInfo.setTotal(repairTotal);
        repairTaskInfo.setCancelTotal(cancelRepair);
        repairTaskInfo.setCompletedTotal(completedRepair);
        repairTaskInfo.setRepair(true);
        repairTaskInfo.setWorkerCount(repairerCount);
        result.add(repairTaskInfo);
        return result;
    }


    /**
     * 统计工单数量
     *
     * @param start
     * @param end
     * @param repair     是否是运维工单
     * @param taskStatus
     * @return
     */
    private int taskCount(LocalDateTime start, LocalDateTime end , Boolean repair , Integer taskStatus ){
        LambdaQueryWrapper<TaskEntity> qw = new LambdaQueryWrapper<>();
        qw.ge(TaskEntity::getUpdateTime,start)
                .le(TaskEntity::getUpdateTime,end);
        //按工单状态查询
        if(taskStatus!=null){
            qw.eq(TaskEntity::getTaskStatus,taskStatus);
        }
        if(repair){//如果是运维工单
            qw.ne(TaskEntity::getProductTypeId,VMSystem.TASK_TYPE_SUPPLY);
        }else{
            qw.eq(TaskEntity::getProductTypeId,VMSystem.TASK_TYPE_SUPPLY);
        }
        return this.count(qw);
    }
}
