package com.lkd.config;

/**
 * 消息队列中的主题配置
 */
public class TopicConfig {

    /**
     * 完成运维工单主题
     */
    public final static String VMS_COMPLETED_TOPIC = "server/vms/completed";


    /**
     * 补货工单主题
     */
    public final static String VMS_SUPPLY_TOPIC = "server/vms/supply";

    /**
     * 状态上报主题
     */
    public final static String VMS_STATUS_TOPIC = "server/vms/status";


    /**
     * 通知出货主题
     */
    public final static String VMS_VEND_OUT_TOPIC = "server/vms/vendout";

    /**
     * 出货结果主题（终端->服务端）
     */
    public final static String VMS_RESULT_TOPIC = "server/vms/result";

    /**
     * 发送到售货机终端 出货协议
     * @param innerCode
     * @return
     */
    public static String getVendoutTopic(String innerCode){
        return "vm/"+innerCode+"/vendout";
    }

    /**
     * 自动创建补货工单消息主题
     */
    public final static String TASK_SUPPLY_TOPIC = "server/task/supply";

    /**
     * 延迟订单主题
     */
    public final static String ORDER_CHECK_TOPIC = "server/order/check";
    /**
     * 下发消息到售货机的主题  vm/tovm/售货机编号
     */
    public final static String TO_VM_TOPIC = "vm/tovm/";

    /**
     *  设备状态消息
     */
    public final static String VM_STATUS_TOPIC = "server/status";


}
