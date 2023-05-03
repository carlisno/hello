package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.lkd.common.VMSystem;

import com.lkd.config.ConsulConfig;
import com.lkd.config.TopicConfig;
import com.lkd.contract.*;

import com.lkd.dao.VendingMachineDao;

import com.lkd.emq.MqttProducer;
import com.lkd.entity.*;
import com.lkd.exception.LogicException;
import com.lkd.http.vo.CreateVMReq;
import com.lkd.service.*;

import com.lkd.utils.DistributedLock;
import com.lkd.utils.UUIDUtils;
import com.lkd.viewmodel.SkuViewModel;
import com.lkd.viewmodel.VMDistance;
import com.lkd.vo.Pager;
import com.lkd.vo.SkuVO;
import com.lkd.vo.VmVO;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VendingMachineServiceImpl extends ServiceImpl<VendingMachineDao, VendingMachineEntity> implements VendingMachineService {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private VmTypeService vmTypeService;

    @Autowired
    private MqttProducer mqttProducer;
    @Autowired
    private ConsulConfig consulConfig;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public boolean add(CreateVMReq vendingMachine) {
        VendingMachineEntity vendingMachineEntity = new VendingMachineEntity();
        vendingMachineEntity.setNodeId(Long.valueOf(vendingMachine.getNodeId()));
        vendingMachineEntity.setVmType(vendingMachine.getVmType());
        NodeEntity nodeEntity = nodeService.getById(vendingMachine.getNodeId());
        if (nodeEntity == null) {
            throw new LogicException("所选点位不存在");
        }
        //复制属性
        BeanUtils.copyProperties(nodeEntity, vendingMachineEntity);
        vendingMachineEntity.setCreateUserId(Long.valueOf(vendingMachine.getCreateUserId()));
        vendingMachineEntity.setInnerCode(UUIDUtils.getUUID());
        vendingMachineEntity.setClientId(UUIDUtils.generateClientId(vendingMachineEntity.getInnerCode()));
        this.save(vendingMachineEntity);
        //创建货道数据
        createChannel(vendingMachineEntity);
        return true;
    }

    /**
     * 创建货道
     *
     * @param vm
     * @return
     */
    private boolean createChannel(VendingMachineEntity vm) {
        VmTypeEntity vmType = vmTypeService.getById(vm.getVmType());
        List<ChannelEntity> channelList = Lists.newArrayList();
        for (int i = 1; i <= vmType.getVmRow(); i++) {
            for (int j = 1; j <= vmType.getVmCol(); j++) {
                ChannelEntity channel = new ChannelEntity();
                channel.setChannelCode(i + "-" + j);
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
        if (vm.getVmStatus().equals(VMSystem.VM_STATUS_RUNNING)) {
            throw new LogicException("改设备正在运营");
        }

        NodeEntity nodeEntity = nodeService.getById(nodeId);
        BeanUtils.copyProperties(nodeEntity, vm);
        return this.updateById(vm);
    }


    @Override
    public Pager<String> getAllInnerCodes(boolean isRunning, long pageIndex, long pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);

        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        if (isRunning) {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .eq(VendingMachineEntity::getVmStatus, 1);
        } else {
            qw.lambda()
                    .select(VendingMachineEntity::getInnerCode)
                    .ne(VendingMachineEntity::getVmStatus, 1);
        }
        this.page(page, qw);
        Pager<String> result = new Pager<>();
        result.setCurrentPageRecords(page.getRecords().stream().map(VendingMachineEntity::getInnerCode).collect(Collectors.toList()));
        result.setPageIndex(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotalCount(page.getTotal());

        return result;
    }

    @Override
    public Pager<VendingMachineEntity> query(Long pageIndex, Long pageSize, Integer status, String innerCode) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VendingMachineEntity> page
                = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageIndex, pageSize);
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(VendingMachineEntity::getVmStatus, status);
        }
        if (!Strings.isNullOrEmpty(innerCode)) {
            queryWrapper.likeLeft(VendingMachineEntity::getInnerCode, innerCode);
        }
        this.page(page, queryWrapper);

        return Pager.build(page);
    }


    @Override
    public VmVO findByInnerCode(String innerCode) {
        LambdaQueryWrapper<VendingMachineEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VendingMachineEntity::getInnerCode, innerCode);
        VendingMachineEntity vm = this.getOne(queryWrapper);
        VmVO vmVO = new VmVO();
        BeanUtils.copyProperties(vm, vmVO);
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
        try {
            UpdateWrapper<VendingMachineEntity> uw = new UpdateWrapper<>();
            uw.lambda()
                    .eq(VendingMachineEntity::getInnerCode, innerCode)
                    .set(VendingMachineEntity::getVmStatus, status);
            this.update(uw);

        } catch (Exception ex) {
            log.error("updateStatus error,innerCode is " + innerCode + " status is " + status, ex);
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
        return skuMap.entrySet().stream().map(entry -> {
                    SkuEntity sku = entry.getKey(); //查询商品
                    SkuVO skuVO = new SkuVO();
                    BeanUtils.copyProperties(sku, skuVO);
                    skuVO.setImage(sku.getSkuImage());//图片
                    skuVO.setCapacity(entry.getValue());
                    skuVO.setRealPrice(sku.getPrice());//真实价格
                    return skuVO;
                }).sorted(Comparator.comparing(SkuVO::getCapacity).reversed())  //按库存量降序排序
                .collect(Collectors.toList());
    }

    @Override
    public Boolean hasCapacity(String innerCode, Long skuId) {
        var qw = new LambdaQueryWrapper<ChannelEntity>();
        qw
                .eq(ChannelEntity::getInnerCode, innerCode)
                .eq(ChannelEntity::getSkuId, skuId)
                .gt(ChannelEntity::getCurrentCapacity, 0);
        return channelService.count(qw) > 0;
    }

    @Override
    public VendingMachineEntity findByClientId(String clientId) {
        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        qw.lambda().eq(VendingMachineEntity::getClientId, clientId);
        return this.getOne(qw);
    }

    /**
     * 执行补货逻辑
     *
     * @param completeContract 补货协议
     */
    @Override
    public void supply(SupplyContract completeContract) {
        //1.更新售货机上一次时间
        UpdateWrapper<VendingMachineEntity> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(VendingMachineEntity::getInnerCode, completeContract.getInnerCode())
                .set(VendingMachineEntity::getLastSupplyTime, LocalDateTime.now());
        update(wrapper);
        //2.更新货道信息
        List<ChannelEntity> channelesByInnerCode =
                channelService.getChannelesByInnerCode(completeContract.getInnerCode());

        List<SupplyChannel> supplyData = completeContract.getSupplyData();
        Map<String, SupplyChannel> collect =
                supplyData.stream().collect(Collectors.toMap(SupplyChannel::getChannelId, each -> each));

        channelesByInnerCode.forEach(each -> {

            if (each.getSkuId() != 0) {
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
            if (CollectionUtils.isEmpty(collect)) {
                log.info("当前售货机不缺货,当前售货机的编码为:{}", entity.getInnerCode());
                return;
            }

            //3.组装消息
            SupplyContract supplyContract = new SupplyContract();
            supplyContract.setInnerCode(entity.getInnerCode());
            supplyContract.setSupplyData(collect);
            mqttProducer.send(TopicConfig.TASK_SUPPLY_TOPIC, 2, supplyContract);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理出货逻辑
     *
     * @param vendoutContract 通知出货协议
     */
    @Override
    public void vendOut(VendoutContract vendoutContract) {
        try {
            List<ChannelEntity> channelesByInnerCode = channelService.getChannelesByInnerCode(vendoutContract.getInnerCode());

            //定位到扣减的商品和对应的货道
            Optional<ChannelEntity> first = channelesByInnerCode.stream()
                    .filter(each -> each.getCurrentCapacity() > 0
                            && each.getSkuId() == vendoutContract.getVendoutData().getSkuId())
                    //findFirst()返回第一个匹配的数据
                    .findFirst();

            if (first.isPresent()) {
                ChannelEntity channelEntity = first.get();

                channelEntity.setCurrentCapacity(channelEntity.getCurrentCapacity() - 1);
                channelService.updateById(channelEntity);
                return;
            } else {
                throw new LogicException("异常");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //解锁售货机状态
            DistributedLock lock = new DistributedLock(
                    consulConfig.getConsulRegisterHost(),
                    consulConfig.getConsulRegisterPort());
            String sessionId = (String) redisTemplate.boundValueOps(
                    VMSystem.VM_LOCK_KEY_PREF + vendoutContract.getInnerCode()
                            + "_" + vendoutContract.getVendoutData().getSkuId()).get();
            log.info("释放锁：{}", sessionId);
            lock.releaseLock(sessionId);
        }

        throw new LogicException("当前货道无库存");
        //todo:发出信息给具体的售货机
    }

    @Override
    public List<SkuViewModel> getSkuList(String innerCode) {

        //货道查询
        List<ChannelEntity> channelList = this.getAllChannel(innerCode)
                .stream()
                .filter(c -> c.getSkuId() > 0 && c.getSku() != null)
                .collect(Collectors.toList());

        //获取商品库存余量

        Map<SkuEntity, Integer> skuMap = channelList.stream().collect(Collectors.groupingBy(ChannelEntity::getSku, Collectors.summingInt(ChannelEntity::getCurrentCapacity)));

        //商品价格表  map   key  商品id
        Map<Long, IntSummaryStatistics> skuPrice =
                channelList.stream().collect(Collectors.groupingBy(ChannelEntity::getSkuId, Collectors.summarizingInt(ChannelEntity::getPrice)));

        return skuMap.entrySet().stream().map(entry -> {
                    SkuEntity sku = entry.getKey();
                    sku.setRealPrice(skuPrice.get(sku.getSkuId()).getMin());//真实价格
                    SkuViewModel skuViewModel = new SkuViewModel();
                    BeanUtils.copyProperties(sku, skuViewModel);
                    skuViewModel.setImage(sku.getSkuImage());
                    skuViewModel.setCapacity(entry.getValue());//库存数
                    return skuViewModel;
                }).sorted(Comparator.comparing(SkuViewModel::getCapacity).reversed())
                .collect(Collectors.toList());

    }

    @Override
    public List<ChannelEntity> getAllChannel(String innerCode) {
        return channelService.getChannelesByInnerCode(innerCode);
    }
    @Autowired
    private VendoutRunningService vendoutRunningService;

    @Transactional
    public boolean vendOutResult(VendoutResp vendoutResp) {
        try {
            String key = "vmService.outResult." + vendoutResp.getVendoutResult().getOrderNo();

            //对结果做校验，防止重复上传(从redis校验)
            Object redisValue = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);

            if (redisValue != null) {
                log.info("出货重复上传");
                return false;
            }


            //存入出货流水数据
            VendoutRunningEntity vendoutRunningEntity = new VendoutRunningEntity();
            vendoutRunningEntity.setInnerCode(vendoutResp.getInnerCode());
            vendoutRunningEntity.setOrderNo(vendoutResp.getVendoutResult().getOrderNo());
            vendoutRunningEntity.setStatus(vendoutResp.getVendoutResult().isSuccess());
            vendoutRunningEntity.setPrice(vendoutResp.getVendoutResult().getPrice());
            vendoutRunningEntity.setSkuId(vendoutResp.getVendoutResult().getSkuId());
            vendoutRunningService.save(vendoutRunningEntity);


            //存入redis
            redisTemplate.opsForValue().set(key, key);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            //减货道库存
            ChannelEntity channel = channelService.getChannelInfo(vendoutResp.getInnerCode(), vendoutResp.getVendoutResult().getChannelId());
            int currentCapacity = channel.getCurrentCapacity() - 1;
            if (currentCapacity < 0) {
                log.info("缺货");
                notifyGoodsStatus(vendoutResp.getInnerCode(), true);

                return true;
            }

            channel.setCurrentCapacity(currentCapacity);
            channelService.updateById(channel);
        } catch (Exception e) {
            log.error("update vendout result error.", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            return false;
        }

        return true;
    }

    /**
     * 发送缺货告警信息
     *
     * @param innerCode
     * @param isFault   true--缺货状态;false--不缺货状态
     */
    private void notifyGoodsStatus(String innerCode, boolean isFault) {
        VmStatusContract contract = new VmStatusContract();
        contract.setNeedResp(false);
        contract.setSn(0);
        contract.setInnerCode(innerCode);

        StatusInfo statusInfo = new StatusInfo();
        statusInfo.setStatus(isFault);
        statusInfo.setStatusCode("10003");
        List<StatusInfo> statusInfos = Lists.newArrayList();
        statusInfos.add(statusInfo);
        contract.setStatusInfo(statusInfos);

        try {
            //  发送设备不缺货消息(置设备为不缺货)
            mqttProducer.send(TopicConfig.VM_STATUS_TOPIC, 2, contract);
        } catch (JsonProcessingException e) {
            log.error("serialize error.", e);

        }
    }

}