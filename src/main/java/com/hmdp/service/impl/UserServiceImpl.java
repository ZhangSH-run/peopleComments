package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements IUserService {
    @Autowired
    private IUserService iUserService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到 redis,设置有效期 5分钟
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone , code ,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            //3.不一致，报错
            return Result.fail("验证码错误");
        }
        // 一致，根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone , phone);
        User user = baseMapper.selectOne(queryWrapper);

        // 5.判断用户是否存在
        if(user == null){
            // 不存在，则创建
            user =  iUserService.createUserWithPhone(phone);
            if (user == null){
                return Result.fail("服务器内部错误，创建用户失败。");
            }
        }
        // 7.保存用户信息到redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString();
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id",userDTO.getId().toString());
        userMap.put("nickName",userDTO.getNickName());
        userMap.put("icon",userDTO.getIcon());
        // 7.3.存储
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        redisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    @Override
    @Transactional
    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        baseMapper.insert(user);
        return user;
    }
}
