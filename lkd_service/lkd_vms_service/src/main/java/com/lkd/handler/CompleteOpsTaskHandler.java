package com.lkd.handler;

import com.lkd.business.MsgHandler;
import com.lkd.common.VMSystem;
import com.lkd.config.TopicConfig;
import com.lkd.contract.TaskCompleteContract;
import com.lkd.emq.Topic;
import com.lkd.service.VendingMachineService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * @Title: CompleteOpsTaskHandler
 * @Author YuanRL
 * @Package com.lkd.handler
 * @description: 接受完成运维工单消息,更新售货机状态
 */
@Component
@Topic(TopicConfig.VMS_COMPLETED_TOPIC)
public class CompleteOpsTaskHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vendingMachineService;
    @Override
    public void process(String jsonMsg) throws IOException {
        //1.json转换成对象
        TaskCompleteContract completeContract = JsonUtil.getByJson(jsonMsg, TaskCompleteContract.class);

        //2.如果是投放工单,更新售货机状态为运营
        if(completeContract.getTaskType() == VMSystem.TASK_TYPE_DEPLOY){
            vendingMachineService.updateStatus(completeContract.getInnerCode(),
                    VMSystem.VM_STATUS_RUNNING);
        }
        //3.如果是撤机工单,更新售货机状态为撤机
        if(completeContract.getTaskType() == VMSystem.TASK_TYPE_REVOKE){
            vendingMachineService.updateStatus(completeContract.getInnerCode(),
                    VMSystem.VM_STATUS_REVOKE);
        }
    }
}
