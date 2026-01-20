package com.hmall.trade.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这个配置类用来声明 延迟交换机MQConstants.DELAY_EXCHANGE_NAME 绑定 队列MQConstants.DELAY_ORDER_QUEUE_NAME 的《死信交换机》《死信队列》
 */
@Configuration
public class DeadLetterConfig {

    // ========== 死信交换机 ==========
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("trade.dlx");
    }

    // ========== 死信队列 ==========
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("trade.dlq.order.failed") // 队列持久化
                          .build();
    }

    // ========== 绑定：死信队列绑定到死信交换机 ==========
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                            .to(deadLetterExchange())
                            .with("order.failed"); // 路由键要和 x-dead-letter-routing-key 一致
    }
}
