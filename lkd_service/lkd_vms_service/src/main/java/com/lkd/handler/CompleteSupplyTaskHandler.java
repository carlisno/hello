package com.lkd.handler;

import com.lkd.business.MsgHandler;
import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyContract;
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
 * @description: 接受完成补货工单消息,更新售货机状态
 * 1.售货机上一次补货时间  2.更新货道信息(时间和数量)
 */
@Component
@Topic(TopicConfig.VMS_SUPPLY_TOPIC)
public class CompleteSupplyTaskHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vendingMachineService;
    @Override
    public void process(String jsonMsg) throws IOException {
        //1.json转换成对象
        SupplyContract completeContract = JsonUtil.getByJson(jsonMsg, SupplyContract.class);

        //更新库存
        vendingMachineService.supply(completeContract);
    }
}
