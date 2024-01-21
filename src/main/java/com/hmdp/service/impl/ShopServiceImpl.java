package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null){
            // 无需坐标查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        // 开始位置
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        // 结束位置
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y), new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from){
            // 没有下一页
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<Long, Distance> distanceMap = new HashMap<>(list.size());
        // 截取结果，实现逻辑分页
        list.stream().skip(from).forEach(result -> {
            // 获取店铺id
            String shopIdStr = result.getContent().getName();
            Long shopId = Long.parseLong(shopIdStr);
            ids.add(shopId);
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
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
