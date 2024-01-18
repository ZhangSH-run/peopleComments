package com.hmdp;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testConnectDB(){
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone , "17624571111");
        List<User> userList = userMapper.selectList(queryWrapper);
        // userService.list(queryWrapper);
        System.out.println(userList);
    }

    @Test
    public void testSaveShop(){
        shopService.saveShopRedis(1L,10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    public void testCatTime(){
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(10);
        // 判断秒杀是否开始
        System.out.println(seckillVoucher.getBeginTime());
        System.out.println(LocalDateTime.now());
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            System.out.println("true");
        }else {
            System.out.println("false");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            System.out.println("true");
        }else {
            System.out.println("false");
        }
    }

    @Test
    public void testChaiXiang(){
        Boolean b1 = true;

        if (BooleanUtil.isTrue(b1)){
            System.out.println("......");
        }
    }
}
