package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserThreadLocal;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private IFollowService followService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取当前登录用户
        Long userId = UserThreadLocal.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        // 判断是否点赞
        if (score != null){
            // 已点赞，取消点赞,数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 把用户从Redis的set集合移除
            if (isSuccess){
                redisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }else {
            // 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 保存用户到Redis的set集合
            if (isSuccess){
                redisTemplate.opsForZSet().add(key,userId.toString() , System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = redisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream()
                .map(item -> Long.parseLong(item))
                .collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOList = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = baseMapper.selectById(id);
        if (blog == null){
            return Result.fail("笔记不存在。");
        }
        // 查询blog所属的用户信息
        this.queryBlogUser(blog);
        this.isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserThreadLocal.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean saveSuccess = this.save(blog);
        if (!saveSuccess){
            return Result.fail("新增笔记失败。");
        }
        Long blogId = blog.getId();
        // 查找所有粉丝
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getFollowUserId , user.getId());
        List<Follow> follows = followService.list(queryWrapper);
        // 没有粉丝，直接返回笔记id
        if (follows == null || follows.isEmpty()){
            return Result.ok(blogId);
        }
        // 推送笔记给所有粉丝
        for (Follow follow : follows) {
            // 获取粉丝id
            Long userId = follow.getUserId();
            // 推送
            redisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId ,
                    blogId.toString() ,System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取登录用户id
        Long userId = UserThreadLocal.getUser().getId();
        // 查询redis中的收件箱 blogIdStringSet
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                RedisConstants.FEED_KEY + userId, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minScore = System.currentTimeMillis();
        offset = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 拿到blogId
            String blogIdStr = typedTuple.getValue();
            blogIds.add(Long.parseLong(blogIdStr));
            // 拿到分数
            long score = typedTuple.getScore().longValue();
            if (minScore > score){
                minScore = score;
            }else if (minScore == score){
                // 设置偏移量
                offset++;
            }
        }
        String blogIdStr = StrUtil.join("," , blogIds);
        List<Blog> blogs = this.query()
                .in("id", blogIds)
                .last("ORDER BY FIELD(id," + blogIdStr + ")").list();
        for (Blog blog : blogs) {
            // 查询blog有关的用户
            this.queryBlogUser(blog);
            // 查询blog是否被点赞
            this.isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minScore);
        scrollResult.setOffset(offset);
        
        return Result.ok(scrollResult);
    }

    /**
     * 查询blog有关的用户
     * @param blog
     */
    private void queryBlogUser(Blog blog){
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询blog是否被点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog){
        // 1.获取当前登录用户
        UserDTO user = UserThreadLocal.getUser();
        if (user == null){
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
