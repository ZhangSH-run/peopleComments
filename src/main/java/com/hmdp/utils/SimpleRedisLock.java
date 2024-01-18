package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String THREAD_PREFIX = UUID.randomUUID()
                                    .toString().replace("-","");
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = THREAD_PREFIX+ ":"  + Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        String localThreadFlag = THREAD_PREFIX+ ":" + Thread.currentThread().getId();
        redisTemplate.execute(UNLOCK_SCRIPT ,
                Collections.singletonList(KEY_PREFIX + name), localThreadFlag);
    }

    /*@Override
    public void unlock() {
        String cacheIdStr = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (StrUtil.isBlank(cacheIdStr)){
            return;
        }
        // 获取线程标识
        String localThreadFlag = THREAD_PREFIX+ ":" + Thread.currentThread().getId();
        if (localThreadFlag.equals(cacheIdStr)){
            redisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
