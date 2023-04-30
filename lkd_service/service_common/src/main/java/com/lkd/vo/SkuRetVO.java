package com.lkd.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SkuRetVO implements Serializable{
    /**
     * 商品Id
     */
    private long skuId;
    /**
     * 商品名称
     */
    private String skuName;

    private Integer count;
}
