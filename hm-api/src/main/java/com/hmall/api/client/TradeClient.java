package com.hmall.api.client;

import com.hmall.api.dto.OrderDetail;
import com.hmall.api.dto.OrderVO;
import com.hmall.common.domain.Order;
import feign.Param;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 这是调用trade-service微服务的openFeign客户端 : TradeClient
 */
@FeignClient(value = "trade-service") //根据服务名称，去拉取实例列表
public interface TradeClient {
    @PutMapping("/orders")
    void updateById(@RequestBody Order order);

    @GetMapping("/orderDetail/{id}")
    List<OrderDetail> queryOrderDetailByIds(@RequestParam("id") Long id);
}
