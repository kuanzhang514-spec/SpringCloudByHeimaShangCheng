package com.hmall.cart.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;
import com.hmall.cart.mapper.CartMapper;
import com.hmall.cart.service.ICartService;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    private final String cartKey = "cart:userId:";  //这是往redis中存放购物车数据时的key值前段

    //注入openFeign的客户端ItemClient
    private final ItemClient itemClient;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否已经存在
        if (checkItemExists(cartFormDTO.getItemId(), userId)) {
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            // 同步：删除 Redis 缓存，触发下次查询时重建
            stringRedisTemplate.delete(cartKey + userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库，此时已经生成了购物车条目id
        save(cart);
        // 同步到 Redis：删除旧缓存
        stringRedisTemplate.delete(cartKey + userId);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        Long userId = UserContext.getUser();  //user的id
        String cartJson = stringRedisTemplate.opsForValue().get(cartKey + userId);
        if (StrUtil.isNotBlank(cartJson)) {
            try {
                // Redis 中有，反序列化并返回
                List<CartVO> cachedVos = JSONUtil.toList(cartJson, CartVO.class);
                return CollUtils.isEmpty(cachedVos) ? CollUtils.emptyList() : cachedVos;
            } catch (Exception e) {
                // JSON 解析失败，可能是脏数据，继续查数据库
                log.warn("Redis cart data parse failed for user: " + userId, e);
            }
        }

        // 1.查询我的购物车列表
        List<Cart> carts = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .list();
        if (CollUtils.isEmpty(carts)) {
            // 数据库也没有，写空列表到 Redis（缓存穿透防护）
            stringRedisTemplate.opsForValue().set(cartKey + userId, "[]", 300, TimeUnit.MINUTES);
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息(补充商品信息)
        handleCartItems(vos);

        //写回redis中
        try {
            String json = JSONUtil.toJsonStr(vos);
            stringRedisTemplate.opsForValue().set(cartKey + userId, json, 300, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 序列化失败也别影响主流程
            log.error("Failed to cache cart for user: " + userId, e);
        }

        // 4.返回
        return vos;
    }

    private void handleCartItems(List<CartVO> vos) {
        // 1.获取商品id
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        // 2.查询商品
        // 跨服务的调用cart->item  itemService.queryItemByIds(itemIds)
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (CollUtils.isEmpty(items)) {
            return;
        }
        // 3.转为 id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        // 4.写入vo
        for (CartVO v : vos) {
            ItemDTO item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);

        // 同步到 Redis：删除旧缓存
        Long userId = UserContext.getUser();  //user的id
        stringRedisTemplate.delete(cartKey + userId);
    }

    @Override
    public void updateCart(Cart cart) {
        this.updateById(cart);
        // 同步到 Redis：删除旧缓存
        Long userId = UserContext.getUser();  //user的id
        stringRedisTemplate.delete(cartKey + userId);
    }

    @Override
    public void deleteCartItem(Long id) {
        this.removeById(id);
        // 同步到 Redis：删除旧缓存
        Long userId = UserContext.getUser();  //user的id
        stringRedisTemplate.delete(cartKey + userId);
    }

    @Override
    public void clear(Long userId, Long itemId) {
        // 构建查询条件：用户 + 商品
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId);

        // 执行删除
        remove(queryWrapper);

        // 同步删除 Redis 缓存（如果用了缓存）
        stringRedisTemplate.delete(cartKey + userId);
    }

    private void checkCartsFull(Long userId) {
        Long count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= 10) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", 10));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        Long count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}
