package com.hmall.trade.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 失败处理策略
 * com.hmall.trade.listener.PayStatusListener 消费者达到最大本地重试次数后得处理
 * RepublishMessageRecoverer，失败后将消息投递到一个指定的，专门存放异常消息的队列，后续由《《人工》》集中处理。
 * <p>
 * exchange --->pay.success.error.direct
 * queue------->pay.success.error.queue
 * routingKey-->pay.error
 * 条件: 在开启消费者失败重试机制的模块才加载的以下的bean
 */
//@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class ErrorMessageConfig {
    @Bean
    public DirectExchange errorMessageExchange() {
        return new DirectExchange("pay.success.error.direct");
    }

    @Bean
    public Queue errorQueue() {
        return new Queue("pay.success.error.queue", true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange) {
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange).with("pay.error");
    }

    /**
     * RepublishMessageRecoverer 处理 消费者失败重试机制 的 重试次数耗尽的兜底方案1
     *
     * @param rabbitTemplate
     * @return
     */
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "pay.success.error.direct", "pay.error");
    }
}
