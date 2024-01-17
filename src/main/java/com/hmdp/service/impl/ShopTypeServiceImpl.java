package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        // 从redis查询缓存
        String shopTypeListJSON = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE + "list");
        // 判断缓存存在
        if (StrUtil.isNotBlank(shopTypeListJSON)){
            // 存在，直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeListJSON, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 不存在，查询数据库
        List<ShopType> shopTypes = baseMapper.selectList(null);
        // 存在，存入redis
        shopTypeListJSON = JSONUtil.toJsonStr(shopTypes);
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE + "list" , shopTypeListJSON,
                RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
        // 返回商铺信息
        return Result.ok(shopTypes);
    }
}
