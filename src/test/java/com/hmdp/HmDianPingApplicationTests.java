package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IUserService userService;
    @Test
    public void test(){
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone , "17624571111");
        List<User> userList = userMapper.selectList(queryWrapper);
        // userService.list(queryWrapper);
        System.out.println(userList);
    }

}
