package com.hmall.pay.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.TradeClient;
import com.hmall.api.client.UserClient;
import com.hmall.api.dto.CartFormDTO;
import com.hmall.api.dto.OrderDetail;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.pay.domain.dto.PayApplyDTO;
import com.hmall.pay.domain.dto.PayOrderFormDTO;
import com.hmall.pay.domain.po.PayOrder;
import com.hmall.pay.enums.PayStatus;
import com.hmall.pay.mapper.PayOrderMapper;
import com.hmall.pay.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 支付订单 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    private final UserClient userClient;
    private final TradeClient tradeClient;
    private final CartClient cartClient;
    private final RabbitTemplate rabbitTemplate;


    @Override
    public String applyPayOrder(PayApplyDTO applyDTO) {
        // 1.幂等性校验
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 2.返回结果
        return payOrder.getId().toString();
    }

    /**
     * 这是在操作-------> 支付单   用户付款的操作
     * 这是在用户付款（上游是com.hmall.trade.service.impl.OrderServiceImpl中创建的订单）
     * 下游是对MQ的监听，异步修改订单状态--->2（交易微服务中的业务了）
     *
     * @param payOrderFormDTO
     */
    @Override
    @Transactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        // 1.查询支付单，在pay的微服务里已经创建好了支付单PayOrder
        PayOrder po = getById(payOrderFormDTO.getId());
        // 2.判断状态
        if (!PayStatus.WAIT_BUYER_PAY.equalsValue(po.getStatus())) {
            // 订单不是未支付，状态异常
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 3.尝试扣减余额
        userClient.deductMoney(payOrderFormDTO.getPw(), po.getAmount());  //这里使用了微服务调用
        // 4.《《修改支付单状态》》 pay_order表儿 《《设置状态为3》》 《《设置状态为3》》 《《设置状态为3》》
        boolean success = markPayOrderSuccess(payOrderFormDTO.getId(), LocalDateTime.now());
        if (!success) {
            throw new BizIllegalException("交易已支付或关闭！");
        }

        //交易成功、在这里清理购物车***
        //1.获取订单id
        Long orderId = po.getBizOrderNo();
        //2.根据订单id查询订单详情表
        List<OrderDetail> orderDetails = tradeClient.queryOrderDetailByIds(orderId); //这里使用了微服务调用
        //3.挨个删除 userId + ItemId一样的给删除
        for (OrderDetail orderDetail : orderDetails) {
            Long itemId = orderDetail.getItemId();
            Long userId = UserContext.getUser();
            cartClient.deleteByClear(userId, itemId);  //这里使用了微服务调用
        }

        // 5.修改订单状态(trade微服务)(这里修改为rabbitMQ异步调用的方式了)
        // 5.修改订单状态(trade微服务)(这里修改为rabbitMQ异步调用的方式了)
        // 5.修改订单状态(trade微服务)(这里修改为rabbitMQ异步调用的方式了)
        /*
         * 如果此时MQ在这里宕机了，那么还有延迟消息死信交换机的兜底方案
         * 在
         * ---> com.hmall.trade.service.impl.OrderServiceImpl#createOrder
         * 这里已经实现了，创建完订单后就发送延迟消息来检查支付单的状态
         *
         *
         * 异步通知 + 定时对账/补偿:
         * 1. 正常流程：
              用户支付成功
                  └─ pay 微服务发送 MQ 消息 (pay.success)
                  └─ trade 微服务消费消息，立即更新订单为“已支付” ✅ 快速响应
           2. 异常流程（MQ 通知失败）：
              pay 发送了消息，但 trade 没收到（消费者宕机、网络抖动等）
                └─ 100s 后，trade 发送的延迟消息触发
                └─ trade 主动查询 pay 的支付状态
                └─ 若已支付 → 手动更新订单
                └─ 若未支付 → 取消订单 + 恢复库存 ✅ 兜底保障
         */
        try {
            //往MQ发送消息 由com.hmall.trade.listener.PayStatusListener监听这个队列
            //开启了生产者确认机制publisher confirm，没必要开启消费者确认机制，因为消费者在trade微服务端（或者说有延迟120s的兜底机制）
            // 1.创建CorrelationData
            CorrelationData cd = new CorrelationData();
            // 2.给Future添加ConfirmCallback
            cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
                @Override
                public void onFailure(Throwable ex) {
                    // 2.1.Future发生异常时的处理逻辑，基本不会触发
                    log.info(UserContext.getUser() + "--->" + po.getBizOrderNo() + "--->" + "pay.direct" + " and " + "pay.success");
                    log.error("com.hmall.pay.service.impl.PayOrderServiceImpl.tryPayOrderByBalance 发送消息失败！", ex);
                }

                @Override
                public void onSuccess(CorrelationData.Confirm result) {
                    //记录日志
                    log.info(UserContext.getUser() + "--->" + po.getBizOrderNo() + "--->" + "pay.direct" + " and " + "pay.success");
                    // 2.2.Future接收到回执的处理逻辑，参数中的result就是回执内容
                    if (result.isAck()) { // result.isAck()，boolean类型，true代表ack回执，false 代表 nack回执
                        log.debug("发送消息成功，收到 ack!");
                    } else { // result.getReason()，String类型，返回nack时的异常描述
                        log.error("发送消息失败，收到 nack, reason : {}", result.getReason());
                    }
                }
            });
            rabbitTemplate.convertAndSend("pay.direct", "pay.success", po.getBizOrderNo(), cd);
        } catch (Exception e) {
            log.info("支付成功的消息发送失败，支付单id：{}， 交易单id：{}", po.getId(), po.getBizOrderNo(), e);
        }
    }

    @Override
    public PayOrderDTO queryByOrderId(Long id) {
        PayOrder payOrder = Db.lambdaQuery(PayOrder.class).eq(PayOrder::getBizOrderNo, id).one();
        return BeanUtil.copyProperties(payOrder, PayOrderDTO.class);
    }

    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        return lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())   //设置为3才代表支付成功了
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
    }


    private PayOrder checkIdempotent(PayApplyDTO applyDTO) {
        // 1.首先查询支付单
        PayOrder oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            save(payOrder);
            return payOrder;
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException("订单已经支付！");
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException("订单已关闭");
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), applyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // 6.旧单已经存在，且可能是未支付或未提交，且支付渠道一致，直接返回旧数据
        return oldOrder;
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        PayOrder payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        payOrder.setBizUserId(UserContext.getUser());
        return payOrder;
    }

    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
