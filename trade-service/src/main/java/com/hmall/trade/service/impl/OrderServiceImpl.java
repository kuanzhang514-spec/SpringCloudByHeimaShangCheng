package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.client.PayClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final IOrderDetailService detailService;
    private final ItemClient itemClient;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 这是在创建-------> 订单 用户下单的操作
     * 接下来还有com.hmall.pay.service.impl.PayOrderServiceImpl中的付款
     * 接下来还有com.hmall.trade.listener.PayStatusListener监听MQ实现修改订单状态
     *
     * @param orderFormDTO
     * @return
     */
    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);  //这里使用了微服务调用
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);   // ***在这里设置订单状态为---->未支付  1
        // 1.6.将Order写入数据库order表中
        save(order);   //完成创建订单的操作 order表儿

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品 支付后再清理
        //cartClient.removeByItemIds(itemIds);   //这里使用了微服务调用

        // 4.扣减对应商品的库存
        try {
            itemClient.deductStock(detailDTOS);  //这里使用了微服务调用
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        //在这里设置延迟消息发送，定时任务，定时查询订单支付状态（trade微服务如果支付了会MQ更新订单状态为2（已支付））。
        //这样即便trade微服务的MQ通知失败，还可以利用定时任务作为兜底方案2，确保订单支付状态的最终一致性
        //开启生产者确认机制publisher confirm,即使失败了也能记录日志
        //对应消费者方--->配置死信交换机，应对消费者重试达到最大失败重试次数导致的消息丢弃问题
        CorrelationData cd = new CorrelationData();
        cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
            @Override
            public void onFailure(Throwable ex) {
                // Future发生异常时的处理逻辑，基本不会触发
                log.info(UserContext.getUser() + "--->" + order.getId() + "--->" + MQConstants.DELAY_EXCHANGE_NAME + " and " + MQConstants.DELAY_ORDER_KEY + ":");
                log.error("com.hmall.trade.service.impl.OrderServiceImpl.createOrder 发送消息失败！", ex);
            }

            @Override
            public void onSuccess(CorrelationData.Confirm result) {
                //记录日志
                log.info(UserContext.getUser() + "--->" + order.getId() + "--->" + MQConstants.DELAY_EXCHANGE_NAME + " and " + MQConstants.DELAY_ORDER_KEY + ":");
                // Future接收到回执的处理逻辑，参数中的result就是回执内容
                if (result.isAck()) { // result.isAck()，boolean类型，true代表ack回执，false 代表 nack回执
                    log.debug("发送消息成功，收到 ack!");
                } else { // result.getReason()，String类型，返回nack时的异常描述
                    log.error("发送消息失败，收到 nack, reason : {}", result.getReason());
                }
            }
        });
        rabbitTemplate.convertAndSend(MQConstants.DELAY_EXCHANGE_NAME, MQConstants.DELAY_ORDER_KEY, order.getId(), new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(120000);  //设置延迟120s（单位ms）
                return message;
            }
        }, cd);
        log.info("---> 往交换机 trade.delay.direct 中发送了一条延时消息, 延时时间:{} s", 120);
        log.info("订单id：{}", order.getId());
        return order.getId();
    }

    /**
     * 根据order 订单id设置订单支付状态为已支付2
     *
     * @param orderId
     */
    @Override
    public void markOrderPaySuccess(Long orderId) {
        // UPDATE `order` SET status = ? , pay_time = ? WHERE id = ? AND status = 1
        //确保业务的幂等性,基于业务逻辑判断--->查询订单判断是否为未支付
        lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
    }

    /**
     * 根据order 订单id设置订单支付状态为取消5
     *
     * @param orderId
     */
    public void markOrderPayCancel(Long orderId) {
        // UPDATE `order` SET status = ? , pay_time = ? WHERE id = ? AND status = 1
        //确保业务的幂等性,基于业务逻辑判断--->查询订单判断是否为未支付
        lambdaUpdate()
                .set(Order::getStatus, 5)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }

    /**
     * 根据订单id orderId
     * 取消订单 + 恢复库存
     *
     * @param orderId
     */
    public void cancelOrder(Long orderId) {
        //1.取消订单---->设置状态为5
        this.markOrderPayCancel(orderId);
        //2.恢复库存 先查询order_detail数据库表来获取都下单了哪些商品
        List<OrderDetail> list = Db.lambdaQuery(OrderDetail.class).eq(OrderDetail::getOrderId, orderId).list();
        //分别拿到下单的商品的 id 和 数量 --->存到Map集合里面
        HashMap<Long, Integer> map = new HashMap<>();
        for (OrderDetail orderDetail : list) {
            Long itemId = orderDetail.getItemId();
            Integer num = orderDetail.getNum();
            map.put(itemId, num);
        }
        //遍历map挨个恢复商品的库存  ---> item-service 调用
        map.forEach(new BiConsumer<Long, Integer>() {
            @Override
            public void accept(Long itemId, Integer itemNum) {
                itemClient.incrStock(itemId, itemNum);    //这里使用了微服务调用
                log.info("商品id: {} 增加了库存：{} 个", itemId, itemNum);
            }
        });
    }
}
