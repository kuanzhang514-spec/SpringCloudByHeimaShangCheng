package com.hmall.api.client;

import com.hmall.api.dto.CartFormDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collection;

/**
 * 这是调用cart-service微服务的openFeign客户端 : CartClient
 */
@FeignClient(value = "cart-service") //根据服务名称，去拉取实例列表
public interface CartClient {
    @DeleteMapping("/carts")
    void removeByItemIds(@RequestParam("ids") Collection<Long> ids);

    @PostMapping("/carts")
    void addItem2Cart(@Valid @RequestBody CartFormDTO cartFormDTO);

    @DeleteMapping("/carts/clear/{userId}/{itemId}")
    void deleteByClear(@PathVariable("userId") Long userId, @PathVariable("itemId") Long itemId);
}
