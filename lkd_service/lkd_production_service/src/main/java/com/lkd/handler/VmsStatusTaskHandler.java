package com.lkd.handler;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.lkd.business.MsgHandler;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.StatusInfo;
import com.lkd.contract.VmStatusContract;
import com.lkd.emq.Topic;
import com.lkd.feign.VMService;
import com.lkd.http.vo.TaskViewModel;
import com.lkd.service.TaskService;
import com.lkd.utils.JsonUtil;
import com.lkd.vo.VmVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Title: VmsStatusTaskHandler
 * @Author YuanRL
 * @Package com.lkd.handler
 * @Date 2022/11/12 23:34
 * @description: 接受售货机状态消息,自动创建维修工单
 */
@Component
@Topic(TopicConfig.VMS_STATUS_TOPIC)
public class VmsStatusTaskHandler implements MsgHandler {

    @Autowired
    private TaskService taskService;
    @Autowired
    private VMService vmService;
    @Override
    public void process(String jsonMsg) throws IOException {
        //1.json转换成对象
        VmStatusContract vmStatusContract = JsonUtil.getByJson(jsonMsg, VmStatusContract.class);

        //2.过滤出false的状态码
        List<StatusInfo> collect = vmStatusContract.getStatusInfo().stream()
                .filter(each -> !each.isStatus())
                .collect(Collectors.toList());
        VmVO vmInfo = vmService.getVMInfo(vmStatusContract.getInnerCode());

        Integer leastUser = taskService.getLeastUser(vmInfo.getRegionId(), Boolean.FALSE);


        if (!CollectionUtils.isEmpty(collect)){
            //创建工单
            TaskViewModel taskViewModel = new TaskViewModel();
            taskViewModel.setCreateType(VMSystem.AUTO_CREATE_TYPE);
            taskViewModel.setInnerCode(vmStatusContract.getInnerCode());
            taskViewModel.setUserId(leastUser);
            taskViewModel.setAssignorId(VMSystem.SYSTEM_AUTO_CREATE_TYPE);
            taskViewModel.setProductType(VMSystem.TASK_TYPE_REPAIR);
            taskViewModel.setDesc(jsonMsg);
            taskService.create(taskViewModel);
        }
    }
}
