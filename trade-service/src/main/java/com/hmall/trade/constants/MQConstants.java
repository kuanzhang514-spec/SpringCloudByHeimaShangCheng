package com.hmall.trade.constants;

/**
 * 这是一个常量类
 * 在OrderServiceImpl中发送延迟消息使用
 */
public interface MQConstants {
    String DELAY_EXCHANGE_NAME = "trade.delay.direct";
    String DELAY_ORDER_QUEUE_NAME = "trade.delay.order.queue";
    String DELAY_ORDER_KEY = "delay.order.query";
}


