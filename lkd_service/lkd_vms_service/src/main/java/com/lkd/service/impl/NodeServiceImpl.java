package com.lkd.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Strings;
import com.lkd.dao.NodeDao;
import com.lkd.entity.NodeEntity;
import com.lkd.entity.VendingMachineEntity;
import com.lkd.service.NodeService;
import com.lkd.service.VendingMachineService;
import com.lkd.vo.NodeRetVo;
import com.lkd.vo.Pager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NodeServiceImpl  extends ServiceImpl<NodeDao, NodeEntity> implements NodeService{

    @Override
    public Pager<NodeEntity> search(String name, String regionId, long pageIndex, long pageSize) {
        Page<NodeEntity> page =  new Page<>(pageIndex,pageSize);
        LambdaQueryWrapper<NodeEntity> queryWrapper = new LambdaQueryWrapper<>();
        if(!Strings.isNullOrEmpty(name)){
            queryWrapper.like(NodeEntity::getName,name);
        }
        if(!Strings.isNullOrEmpty(regionId)){
            Long regionIdLong = Long.valueOf(regionId);
            queryWrapper.eq(NodeEntity::getRegionId,regionIdLong);
        }
        return Pager.build(this.page(page,queryWrapper));
    }

    @Autowired
    private VendingMachineService vmService;

    @Override
    public List<VendingMachineEntity> getVmList(long id) {
        QueryWrapper<VendingMachineEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(VendingMachineEntity::getNodeId,id);

        return vmService.list(qw);
    }

    @Override
    public List<NodeRetVo> nodeCollect() {
        var qw = new QueryWrapper<NodeEntity>();
        qw
                .select("count(*) as create_user_id ,owner_id,  owner_name")
                .lambda()
                .groupBy(NodeEntity::getOwnerId)
                .groupBy(NodeEntity::getOwnerName)
                .orderByDesc(NodeEntity::getCreateUserId)
                .last("limit 10");
        var result = this
                .list(qw)
                .stream()
                .map(t->{
                    var nodeRetVo = new NodeRetVo();
                    nodeRetVo.setName(t.getOwnerName());
                    nodeRetVo.setValue((int)t.getCreateUserId());
                    return nodeRetVo;
                }).collect(Collectors.toList());

        return result;
    }

    @Override
    public Integer nodeCount() {
        int count = this.count();
        return count;
    }


}
