package com.lkd.handler;

import com.lkd.business.MsgHandler;
import com.lkd.config.TopicConfig;
import com.lkd.contract.TaskCompleteContract;
import com.lkd.contract.VendoutContract;
import com.lkd.emq.Topic;
import com.lkd.service.VendingMachineService;
import com.lkd.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Title: VendOutHandler
 * @Author YuanRL
 * @Package com.lkd.handler
 * @Date 2022/11/14 19:01
 * @description: 接收出货消息,进行库存的扣减
 */
@Component
@Topic(TopicConfig.VMS_VEND_OUT_TOPIC)
public class VendOutHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vendingMachineService;

    @Override
    public void process(String jsonMsg) throws IOException {
        //1.json转换对象
        VendoutContract vendoutContract = JsonUtil.getByJson(jsonMsg, VendoutContract.class);

        //2.库存扣减
        vendingMachineService.vendOut(vendoutContract);
    }
}
