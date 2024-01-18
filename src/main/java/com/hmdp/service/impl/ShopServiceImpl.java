package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public Result queryById(Long id) {
        // 从redis查询缓存
        String shopJSON = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 判断缓存存在
        if (StrUtil.isNotBlank(shopJSON)) {
            // 存在，直接返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        if (shopJSON != null){
            return Result.fail("店铺信息不存在");
        }
        // 不存在，获取互斥锁
        boolean flag = this.tryLock(RedisConstants.LOCK_SHOP_KEY + id);
        // 拿不到锁，原地等待
        if (!flag){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }finally {
                this.unLock(RedisConstants.LOCK_SHOP_KEY + id);
            }
            return this.queryById(id);
        }
        // 拿到锁，查询数据库
        Shop shop = baseMapper.selectById(id);
        // 判断数据库数据是否存在
        if (shop == null) {
            // 不存在
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺信息有误！");
        }
        // 存在，存入redis
        shopJSON = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, shopJSON,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 释放锁
        this.unLock(RedisConstants.LOCK_SHOP_KEY + id);
        // 返回商铺信息
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null){
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        baseMapper.updateById(shop);
        // 删除缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shopId);
        return Result.ok();
    }

    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "true", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        redisTemplate.delete(key);
    }

    public void saveShopRedis(Long id , Long expireSeconds){
        // 查询店铺数据
        Shop shop = baseMapper.selectById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        // 写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id ,
                JSONUtil.toJsonStr(redisData));

    }
}
