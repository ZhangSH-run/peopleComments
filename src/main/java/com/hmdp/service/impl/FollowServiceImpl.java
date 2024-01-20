package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        UserDTO user = UserThreadLocal.getUser();
        if (user == null){
            return Result.fail("请先登录。");
        }
        Long userId = user.getId();
        if (isFollow){
            // 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = this.save(follow);
            if (isSuccess){
                // 把当前用户的关注的id，存入redis的set集合
                redisTemplate.opsForSet().add(RedisConstants.FOLLOWS_KEY + userId ,
                        followUserId.toString());
            }
        }else {
            // 取消关注，删除数据
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId , userId);
            queryWrapper.eq(Follow::getFollowUserId , followUserId);
            boolean isSuccess = this.remove(queryWrapper);
            if (isSuccess){
                // 把关注用户的id从Redis集合中移除
                redisTemplate.opsForSet().remove(RedisConstants.FOLLOWS_KEY + userId ,
                        followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserThreadLocal.getUser().getId();

        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId , userId);
        queryWrapper.eq(Follow::getFollowUserId , followUserId);
        Long count = baseMapper.selectCount(queryWrapper);
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserThreadLocal.getUser().getId();
        String userKey = RedisConstants.FOLLOWS_KEY + userId;
        String followKey = RedisConstants.FOLLOWS_KEY + id;
        Set<String> intersect = redisTemplate.opsForSet().intersect(userKey, followKey);
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream()
                .map(item -> Long.parseLong(item))
                .collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
