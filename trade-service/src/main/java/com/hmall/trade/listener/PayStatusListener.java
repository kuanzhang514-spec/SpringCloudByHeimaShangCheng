package com.hmall.trade.listener;

import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

/**
 * 这是交易微服务
 * MQ的消费者
 * 作用: 把--->订单 支付状态改为已支付2
 *
 *
 *  *  @Queue 中  durable = "true" 持久化队列
 *  *  @Queue 中  arguments = @Argument(name = "x-queue-mode", value = "lazy") 惰性队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayStatusListener {

    private final IOrderService orderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "trade.pay.success.queue", durable = "true", arguments = @Argument(name = "x-queue-mode", value = "lazy")),
            exchange = @Exchange(name = "pay.direct"),
            key = "pay.success"
    ))
    public void listenPaySuccess(Long orderId) {
        log.info("接收到trade.pay.success.queue MQ消息orderId：{}", orderId);
        //调用自己的接口方法 把订单状态从1（未支付）《《改为2（已支付）》》  完成订单状态同步
        orderService.markOrderPaySuccess(orderId);
    }
}
