package com.hmall.trade.listener;

import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 延迟交换机
 * 接收com.hmall.trade.service.impl.OrderServiceImpl#createOrder(com.hmall.trade.domain.dto.OrderFormDTO)发来的消息
 *
 *  * @Exchange 中 delayed = "true" 延迟交换机
 *  * @Queue 中  durable = "true" 持久化队列
 *  * @Queue 中  arguments = @Argument(name = "x-queue-mode", value = "lazy") 惰性队列
 *
 *  死信交换机的配置：
 *   队列用x-dead-letter-exchange属性指定死信交换机trade.dlx
 *   如果这个队列里的某条消息最终被消费者拒绝了（比如重试3次都失败），那就不要丢弃它，而是把它转发给名为 trade.dlx 的交换机。
 *   x-dead-letter-routing-key：死信路由键
 *   在com.hmall.trade.config.DeadLetterConfig中配置好了死信交换机及队列绑定关系bean
 *
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderDelayMessageListener {
    private final OrderServiceImpl orderService;
    private final PayClient payClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    name = MQConstants.DELAY_ORDER_QUEUE_NAME,
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-queue-mode", value = "lazy"),
                            @Argument(name = "x-dead-letter-exchange", value = "trade.dlx"),
                            @Argument(name = "x-dead-letter-routing-key", value = "order.failed")
                    }
            ),
            exchange = @Exchange(name = MQConstants.DELAY_EXCHANGE_NAME, delayed = "true"),
            key = MQConstants.DELAY_ORDER_KEY
    ))
    public void listenerOrderDelayMessage(Long orderId) {

        //log.info("--->故意的抛异常");
        //throw new RuntimeException("模拟故意的抛异常" + LocalDateTime.now());

        log.info("---> 交换机 trade.delay.direct 收到了延时消息:{} ", orderId);
        //1.先检查本地订单(order)的状态是否是2  order表儿   !1--->齐活儿(支付成功了，且通知到位了)   =1--->
        Order order = orderService.getById(orderId);
        if (order == null || order.getStatus() != 1) {
            // 订单不存在 || 订单已支付
            return;
        }

        //2.再检查支付流水(pay_order)状态 pay_order表儿  3--->支付成功了（只是通知没到位）那就手动设置order表的状态为2    !3--->取消订单/恢复库存
        PayOrderDTO payOrder = payClient.queryPayOrderByBizOrderNo(orderId);  //这里使用了微服务调用
        if (payOrder != null && payOrder.getStatus() == 3) {
            //手动设置order表的状态为2
            log.info("手动设置order表的状态为2 id：{}", orderId);
            orderService.markOrderPaySuccess(orderId);
        } else {
            // 取消订单 + 恢复库存
            log.info("取消订单 id：{}", orderId);
            orderService.cancelOrder(orderId);
        }
    }
}
