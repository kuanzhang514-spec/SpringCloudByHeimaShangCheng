package com.hmall.item.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.api.client.ResearchClient;
import com.hmall.common.domain.*;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.domain.dto.OrderDetailDTO;
import com.hmall.item.service.IItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品管理相关接口")
@Slf4j
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;

    private final ResearchClient researchClient;

    @ApiOperation("分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
        // 1.分页查询
        Page<Item> result = itemService.page(query.toMpPage("update_time", false));
        // 2.封装并返回
        return PageDTO.of(result, ItemDTO.class);
    }

    @ApiOperation("根据id批量查询商品")
    @GetMapping
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") List<Long> ids) {
        return itemService.queryItemByIds(ids);
    }

    @ApiOperation("根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        return BeanUtils.copyBean(itemService.getById(id), ItemDTO.class);
    }

    @ApiOperation("新增商品")
    @PostMapping
    public void saveItem(@RequestBody ItemDTO itemDTO) {
        Item item = BeanUtils.copyBean(itemDTO, Item.class);
        // 新增
        itemService.save(item);

        //更新es  新增文档
        try {
            researchClient.add(BeanUtils.copyProperties(item, ItemDoc.class));    //这里发起了微服务远程调用
        } catch (Exception e) {
            log.info("新增同步到es失败:{}", itemDTO);
            log.info("失败原因:{}", e);
        }
    }

    @ApiOperation("更新商品状态")
    @PutMapping("/status/{id}/{status}")
    public void updateItemStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        Item item = new Item();
        item.setId(id);
        item.setStatus(status);
        itemService.updateById(item);

        //更新es status--->1 新增文档  其余status--->删除文档(根据id)(因为只有status值为1才加入es文档中)
        try {
            if (status == 1) {
                researchClient.add(BeanUtils.copyProperties(itemService.getById(id), ItemDoc.class));    //这里发起了微服务远程调用
            } else {
                researchClient.deleteDocById(id);    //这里发起了微服务远程调用
            }
        } catch (Exception e) {
            log.info("修改同步到es失败:{}", id);
            log.info("失败原因:{}", e);
        }
    }

    @ApiOperation("更新商品")
    @PutMapping
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        itemService.updateById(BeanUtils.copyBean(item, Item.class));

        // 更新es  修改文档信息
        try {
            researchClient.add(BeanUtils.copyProperties(itemService.getById(item.getId()), ItemDoc.class));    //这里发起了微服务远程调用
        } catch (Exception e) {
            log.info("修改同步到es失败:{}", item);
            log.info("失败原因:{}", e);
        }
    }

    @ApiOperation("根据id删除商品")
    @DeleteMapping("{id}")
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.removeById(id);

        // 更新es  删除文档
        try {
            researchClient.deleteDocById(id);    //这里发起了微服务远程调用
        } catch (Exception e) {
            log.info("删除同步到es失败:{}", id);
            log.info("失败原因:{}", e);
        }
    }

    @ApiOperation("批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items) {
        itemService.deductStock(items);

        // 更新es 修改文档
        try {
            for (OrderDetailDTO item : items) {
                researchClient.add(BeanUtils.copyProperties(itemService.getById(item.getItemId()), ItemDoc.class));    //这里发起了微服务远程调用
            }
        } catch (Exception e) {
            log.info("扣库存修改同步到es失败:{}", items);
            log.info("失败原因:{}", e);
        }
    }

    /**
     * 恢复库存的功能
     * 根据商品id增加商品的库存
     *
     * @param itemId
     * @param itemNum
     */
    @GetMapping("/stock/incr")
    public void incrStock(@RequestParam("itemId") Long itemId, @RequestParam("itemNum") Integer itemNum) {
        itemService.incrStock(itemId, itemNum);

        // 更新es 修改文档
        try {
            researchClient.add(BeanUtils.copyProperties(itemService.getById(itemId), ItemDoc.class));    //这里发起了微服务远程调用
        } catch (Exception e) {
            log.info("恢复库存修改同步到es失败: id {}, num {}", itemId, itemNum);
            log.info("失败原因:{}", e);
        }
    }
}
