package com.lkd.handler;

import com.lkd.business.MsgHandler;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyContract;
import com.lkd.emq.Topic;
import com.lkd.feign.VMService;
import com.lkd.http.vo.TaskDetailsViewModel;
import com.lkd.http.vo.TaskViewModel;
import com.lkd.service.TaskService;
import com.lkd.utils.JsonUtil;
import com.lkd.vo.VmVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Title: VmsStatusTaskHandler
 * @Author YuanRL
 * @Package com.lkd.handler
 * @Date 2022/11/12 23:34
 * @description: 接收自动补货工单消息, 创建补货工单
 */
@Component
@Topic(TopicConfig.TASK_SUPPLY_TOPIC)
public class VmsSupplyTaskHandler implements MsgHandler {

    @Autowired
    private TaskService taskService;
    @Autowired
    private VMService vmService;

    @Override
    public void process(String jsonMsg) throws IOException {
        //1.json转换成对象
        SupplyContract vmStatusContract = JsonUtil.getByJson(jsonMsg, SupplyContract.class);

        VmVO vmInfo = vmService.getVMInfo(vmStatusContract.getInnerCode());
        Integer leastUser = taskService.getLeastUser(vmInfo.getRegionId(), Boolean.TRUE);

        //2.填充创建工单参数
        TaskViewModel taskViewModel = new TaskViewModel();
        taskViewModel.setCreateType(VMSystem.AUTO_CREATE_TYPE);
        taskViewModel.setInnerCode(vmStatusContract.getInnerCode());
        taskViewModel.setUserId(leastUser);
        taskViewModel.setAssignorId(VMSystem.SYSTEM_AUTO_CREATE_TYPE);
        taskViewModel.setProductType(VMSystem.TASK_TYPE_SUPPLY);
        taskViewModel.setDesc("自动创建工单");
        ArrayList<TaskDetailsViewModel> detailsViewModels = new ArrayList<>();
        taskViewModel.setDetails(detailsViewModels);

        List<TaskDetailsViewModel> collect = vmStatusContract.getSupplyData().stream().map(each -> {
            TaskDetailsViewModel taskDetailsViewModel = new TaskDetailsViewModel();
            taskDetailsViewModel.setChannelCode(each.getChannelId());
            taskDetailsViewModel.setExpectCapacity(each.getCapacity());
            taskDetailsViewModel.setSkuId(each.getSkuId());
            taskDetailsViewModel.setSkuName(each.getSkuName());
            taskDetailsViewModel.setSkuImage(each.getSkuImage());
            return taskDetailsViewModel;
        }).collect(Collectors.toList());

        taskViewModel.setDetails(collect);
        //调用创建工单(补货工单)
        taskService.create(taskViewModel);
    }
}
