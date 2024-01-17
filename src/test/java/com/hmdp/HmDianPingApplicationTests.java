package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private IUserService userService;
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

}
