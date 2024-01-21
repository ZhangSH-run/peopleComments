package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest
public class insertShopGEO {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IShopService shopService;
    @Test
    public void insertShopToGeo(){
        // 查询所有店铺信息
        List<Shop> shopList = shopService.list();
        // 按照店铺类型分类
        Map<Long , List<Shop>> map = shopList.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 根据类型作为key，存储到redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()){
            Long typeId = entry.getKey();
            List<Shop> value = entry.getValue();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations  = new ArrayList<>(value.size());
            for (Shop shop : value) {
                // redisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()) ,shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString() ,
                        new Point(shop.getX(), shop.getY()))
                );
            }
            redisTemplate.opsForGeo().add(key , locations);
        }
    }

    @Test
    public void testDate(){
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        System.out.println("now = " + now);
        System.out.println("month = " + month);
        System.out.println("day = " + day);
    }
}
