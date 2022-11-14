package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;

import com.lkd.config.TopicConfig;
import com.lkd.contract.SupplyChannel;
import com.lkd.contract.SupplyContract;

import com.lkd.dao.VendingMachineDao;

import com.lkd.emq.MqttProducer;
import com.lkd.entity.*;
import com.lkd.exception.LogicException;
import com.lkd.http.vo.CreateVMReq;
import com.lkd.service.*;

import com.lkd.utils.UUIDUtils;
import com.lkd.vo.Pager;
import com.lkd.vo.SkuVO;
import com.lkd.vo.VmVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VendingMachineServiceImpl extends ServiceImpl<VendingMachineDao,VendingMachineEntity> implements VendingMachineService{

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VmTypeService vmTypeService;

    @Autowired
    private MqttProducer mqttProducer;

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean add(CreateVMReq vendingMachine) {
        VendingMachineEntity vendingMachineEntity = new VendingMachineEntity();
        vendingMachineEntity.setNodeId(Long.valueOf(vendingMachine.getNodeId()));
        vendingMachineEntity.setVmType(vendingMachine.getVmType());
        NodeEntity nodeEntity = nodeService.getById(vendingMachine.getNodeId());
        if(nodeEntity == null){
            throw new LogicException("所选点位不存在");
        }
        //复制属性
        BeanUtils.copyProperties(nodeEntity, vendingMachineEntity );
        vendingMachineEntity.setCreateUserId(Long.valueOf(vendingMachine.getCreateUserId()));
        vendingMachineEntity.setInnerCode(UUIDUtils.getUUID());
        vendingMachineEntity.setClientId(UUIDUtils.generateClientId( vendingMachineEntity.getInnerCode() ));
        this.save(vendingMachineEntity);
        //创建货道数据
        createChannel(vendingMachineEntity);
        return true;
    }

    /**
     * 创建货道
     * @param vm
     * @return
     */
    private boolean createChannel(VendingMachineEntity vm){
        VmTypeEntity vmType = vmTypeService.getById(vm.getVmType());
        List<ChannelEntity> channelList= Lists.newArrayList();
        for(int i = 1; i <= vmType.getVmRow(); i++) {
            for(int j = 1; j <= vmType.getVmCol(); j++) {
                ChannelEntity channel = new ChannelEntity();
                channel.setChannelCode(i+"-"+j);
                channel.setCurrentCapacity(0);
                channel.setInnerCode(vm.getInnerCode());
                channel.setLastSupplyTime(vm.getLastSupplyTime());
                channel.setMaxCapacity(vmType.getChannelMaxCapacity());
                channel.setVmId(vm.getId());
                channelList.add(channel);
            }
        }
        channelService.saveBatch(channelList);
        return true;
    }


    @Override
    public boolean update(Long id, Long nodeId) {
        VendingMachineEntity vm = this.getById(id);
        if(vm.getVmStatus().equals(VMSystem.VM_STATUS_RUNNING)){
            throw new LogicException("改设备正在运营");
        }

        NodeEntity nodeEntity = nodeService.getById(nodeId);
        BeanUtils.copyProperties( nodeEntity,vm );
        return this.updateById(vm);
    }


    @Override
    public Pager<String> getAllInnerCodes(boolean isRunning, long pageIndex, long pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex,pageSize);

        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        if(isRunning){
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .eq(VendingMachineEntity::getVmStatus,1);
        }else {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .ne(VendingMachineEntity::getVmStatus,1);
        }
        this.page(page,qw);
        Pager<String> result = new Pager<>();
        result.setCurrentPageRecords(page.getRecords().stream().map(VendingMachineEntity::getInnerCode).collect(Collectors.toList()));
        result.setPageIndex(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotalCount(page.getTotal());

        return result;
    }

    @Override
    public Pager<VendingMachineEntity> query(Long pageIndex, Long pageSize, Integer status,String innerCode) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page
                = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex,pageSize);
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        if(status != null){
            queryWrapper.eq(VendingMachineEntity::getVmStatus,status);
        }
        if(!Strings.isNullOrEmpty(innerCode)){
            queryWrapper.likeLeft(VendingMachineEntity::getInnerCode,innerCode);
        }
        this.page(page,queryWrapper);

        return Pager.build(page);
    }


    @Override
    public VmVO findByInnerCode(String innerCode) {
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getInnerCode,innerCode);
        VendingMachineEntity vm = this.getOne(queryWrapper);
        VmVO vmVO=new VmVO();
        BeanUtils.copyProperties(vm,vmVO);
        //地址
        vmVO.setNodeAddr(vm.getNode().getAddr());
        //名称
        vmVO.setNodeName(vm.getNode().getName());
        vmVO.setRegionName(vm.getRegion().getName());
        vmVO.setBusinessName(vm.getNode().getBusinessType().getName());
        return vmVO;
    }


    @Override
    public boolean updateStatus(String innerCode, Integer status) {
        try{
            UpdateWrapper<VendingMachineEntity> uw = new UpdateWrapper<>();
            uw.lambda()
                    .eq(VendingMachineEntity::getInnerCode,innerCode)
                    .set(VendingMachineEntity::getVmStatus,status);
            this.update(uw);

        }catch (Exception ex){
            log.error("updateStatus error,innerCode is " + innerCode + " status is " + status,ex);
            return false;
        }
        return true;
    }


    @Override
    public List<SkuVO> getSkuListByInnerCode(String innerCode) {
        //获取货道列表
        List<ChannelEntity> channelList = channelService.getChannelesByInnerCode(innerCode).stream()
                .filter(c -> c.getSkuId() > 0 && c.getSku() != null).collect(Collectors.toList());
        //获取有商品的库存余量
        Map<SkuEntity, Integer> skuMap = channelList.stream()
                .collect(Collectors.groupingBy(
                        ChannelEntity::getSku,
                        Collectors.summingInt(ChannelEntity::getCurrentCapacity)));//对库存数求和
        return skuMap.entrySet().stream().map( entry->{
                    SkuEntity sku = entry.getKey(); //查询商品
                    SkuVO skuVO = new SkuVO();
                    BeanUtils.copyProperties( sku,skuVO );
                    skuVO.setImage(sku.getSkuImage());//图片
                    skuVO.setCapacity( entry.getValue() );
                    skuVO.setRealPrice(sku.getPrice());//真实价格
                    return  skuVO;
                } ).sorted(Comparator.comparing(SkuVO::getCapacity).reversed())  //按库存量降序排序
                .collect(Collectors.toList());
    }

    @Override
    public Boolean hasCapacity(String innerCode, Long skuId) {
        var qw = new LambdaQueryWrapper<ChannelEntity>();
        qw
                .eq(ChannelEntity::getInnerCode,innerCode)
                .eq(ChannelEntity::getSkuId,skuId)
                .gt(ChannelEntity::getCurrentCapacity,0);
        return channelService.count(qw) > 0;
    }

    @Override
    public VendingMachineEntity findByClientId(String clientId) {
        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        qw.lambda().eq( VendingMachineEntity::getClientId ,clientId );
        return this.getOne(qw);
    }

    /**
     * 执行补货逻辑
     * @param completeContract 补货协议
     */
    @Override
    public void supply(SupplyContract completeContract) {
        //1.更新售货机上一次时间
        UpdateWrapper<VendingMachineEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(VendingMachineEntity::getInnerCode,completeContract.getInnerCode())
                .set(VendingMachineEntity::getLastSupplyTime,LocalDateTime.now());
        update(wrapper);
        //2.更新货道信息
        List<ChannelEntity> channelesByInnerCode =
                channelService.getChannelesByInnerCode(completeContract.getInnerCode());

        List<SupplyChannel> supplyData = completeContract.getSupplyData();
        Map<String, SupplyChannel> collect =
                supplyData.stream().collect(Collectors.toMap(SupplyChannel::getChannelId, each -> each));

        channelesByInnerCode.forEach(each->{

            if (each.getSkuId()!=0) {
                SupplyChannel supplyChannel = collect.get(each.getChannelCode());
                //修改当前的库存和时间
                each.setCurrentCapacity(each.getCurrentCapacity() + supplyChannel.getCapacity());
                each.setLastSupplyTime(LocalDateTime.now());
                channelService.updateById(each);
            }
        });

    }

    @Override
    public void computeAndSendMsg(VendingMachineEntity entity) {

        try {
            //1.获取当前售货机的所有货道信息
            List<ChannelEntity> channelEntities =
                    channelService.getChannelesByInnerCode(entity.getInnerCode());

            //2.判断货道是否缺货,获得需要补货的售货机的补货信息
            List<SupplyChannel> collect = channelEntities.stream().filter(each ->
                    each.getCurrentCapacity() < each.getMaxCapacity() && each.getSkuId() != 0
            ).map(channelEntity -> {
                SupplyChannel supplyChannel = new SupplyChannel();
                supplyChannel.setChannelId(channelEntity.getChannelCode());
                supplyChannel.setCapacity(channelEntity.getMaxCapacity() - channelEntity.getCurrentCapacity());
                supplyChannel.setSkuId(channelEntity.getSkuId());
                supplyChannel.setSkuName(channelEntity.getSku().getSkuName());
                supplyChannel.setSkuImage(channelEntity.getSku().getSkuImage());
                return supplyChannel;
            }).collect(Collectors.toList());
            //2.1判断需要补货的机器是否为空
            if (CollectionUtils.isEmpty(collect)){
                log.info("当前售货机不缺货,当前售货机的编码为:{}",entity.getInnerCode());
                return;
            }

            //3.组装消息
            SupplyContract supplyContract = new SupplyContract();
            supplyContract.setInnerCode(entity.getInnerCode());
            supplyContract.setSupplyData(collect);
            mqttProducer.send(TopicConfig.TASK_SUPPLY_TOPIC,2,supplyContract);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


}
