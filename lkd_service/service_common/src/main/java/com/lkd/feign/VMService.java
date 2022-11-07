package com.lkd.feign;

import com.lkd.feign.fallback.VmServiceFallbackFactory;
import com.lkd.vo.SkuVO;
import com.lkd.vo.VmVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(value = "vm-service",fallbackFactory = VmServiceFallbackFactory.class)
public interface VMService{

    /**
     * 根据售货机编码查询单个售货机相关信息
     * @param innerCode
     * @return
     */
    @GetMapping("/vm/info/{innerCode}")
    VmVO getVMInfo(@PathVariable String innerCode);

    /**
     * 根据售货机编码查询商品信息列表
     * @param innerCode
     * @return 商品列表
     */
    @GetMapping("/vm/skuList/{innerCode}")
    List<SkuVO> getSkuListByInnerCode(@PathVariable String innerCode);

    /**
     * 根据商品id查询商品
     * @param skuId
     * @return
     */
    @GetMapping("/sku/{skuId}")
    SkuVO getSku(@PathVariable String skuId);

    /**
     * 根据售货机编码+商品id 查询当前商品是否还有库存
     * @param innerCode 售货机编号
     * @param skuId 商品Id
     * @return
     */

    @GetMapping("/vm/hasCapacity/{innerCode}/{skuId}")
    Boolean hasCapacity(@PathVariable String innerCode,@PathVariable Long skuId);

    /**
     * 获取点位名称
     * @param nodeId 点位Id
     * @return 点位名称
     */
    @GetMapping("/node/nodeName/{id}")
    String getNodeName(@PathVariable Long nodeId);


}
