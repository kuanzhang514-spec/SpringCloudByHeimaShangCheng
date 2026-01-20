package com.hmall.api.client;

import com.hmall.common.domain.Item;
import com.hmall.common.domain.ItemDoc;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 这是调用research-service微服务的openFeign客户端 : ResearchClient
 */
@FeignClient(value = "research-service") //根据服务名称，去拉取实例列表
public interface ResearchClient {

    @PostMapping("/_doc/add")
    boolean add(@RequestBody ItemDoc itemDoc);

    @DeleteMapping("/_doc/delete/{id}")
    void deleteDocById(@PathVariable("id") Long id);

}
