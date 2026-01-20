package com.hmall.trade.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 监听死信队列
 *
 * 消息到了 死信队列trade.dlq.order.failed 的人工处理
 */
@Component
@Slf4j
public class DeadLetterListener {

    @RabbitListener(queues = "trade.dlq.order.failed")
    public void handleFailedOrder(Long orderId) {
        log.error("订单 {} 在延迟消息处理中多次失败，已进入死信队列，请人工介入或自动补偿！", orderId);
        /*
         * 这里可以添加错误信息到mySql的一张表中
         *
         * ...
         */
    }
}
