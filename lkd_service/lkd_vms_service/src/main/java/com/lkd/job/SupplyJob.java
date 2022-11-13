package com.lkd.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lkd.common.VMSystem;
import com.lkd.entity.VendingMachineEntity;
import com.lkd.service.VendingMachineService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.util.ShardingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Title: SupplyJob
 * @Author YuanRL
 * @Package com.lkd.job
 * @Date 2022/11/13 21:49
 * @description: 自动补货工单触发
 */
@Component
@Slf4j
public class SupplyJob {

    @Autowired
    private VendingMachineService vendingMachineService;

    @XxlJob("supplyJobHandler")
    public ReturnT<String> supplyJobHandler(String param){
        //1.获取所有在运营的售货机
        QueryWrapper<VendingMachineEntity> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(VendingMachineEntity::getVmStatus, VMSystem.VM_STATUS_RUNNING);
        List<VendingMachineEntity> list = vendingMachineService.list(wrapper);

        //2.货道信息,计算是否缺货,生成对应的消息体,发送消息
        list.forEach(each->{
            //todo:计算是否缺货
            vendingMachineService.computeAndSendMsg(each);
        });
//        ShardingUtil.ShardingVO shardingVO = ShardingUtil.getShardingVo();
//        //分片总数
//        int total = shardingVO.getTotal();
//
//        //当前分片的索引
//        int index = shardingVO.getIndex();
//
//
//        QueryWrapper<VendingMachineEntity> wrapper = new QueryWrapper<>();
//        wrapper.lambda()
//                .eq(VendingMachineEntity::getVmStatus, VMSystem.VM_STATUS_RUNNING)
//                .apply("mod(id,"+total+")"+index);
//        List<VendingMachineEntity> list = vendingMachineService.list(wrapper);
//        log.info("total={},index={},list={}",total,index,list);
//        //2.货道信息,计算是否缺货,生成对应的消息体,发送消息
//        list.forEach(each->{
//            vendingMachineService.computeAndSendMsg(each);
//        });
        return ReturnT.SUCCESS;
    }
}
