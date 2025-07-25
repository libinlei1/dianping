package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //不同key添加额外随机过期时间，防止同时过期导致崩溃
    private Long addRandomTime(Long baseTime){
        Random random = new Random();
        return baseTime+random.nextInt(30);
    }

//    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
//        //设置逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(value);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
//        //写入Redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
//    }

    private void setWithLogicalAndRealExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));//30分钟后逻辑过期，更新
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), addRandomTime(time), unit);//30+random(30)分钟后真正过期
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tyrLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R, ID> R queryWithPassThroughAndLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
       log.info("从Redis中查询店铺缓存");
        String key = keyPrefix + id;
        // 1.从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存中是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，反序列化为RedisData对象
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            LocalDateTime expireTime = redisData.getExpireTime();
            // 4.判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 4.1.未过期，直接返回
                return r;
            } else {
                // 4.2.已过期，需要缓存重建
                // 5.获取互斥锁
                String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
                boolean isLock = tyrLock(lockKey);
                // 6.判断是否获取锁成功
                if (isLock) {
                    // 7.成功，开启独立线程，实现缓存重建
                    CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            // 查询数据库
                            R r1 = dbFallback.apply(id);
                            // 判断数据库中是否存在
                            if (r1 == null) {
                                // 数据库中不存在，将空值写入Redis
                                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                            } else {
                                // 数据库中存在，写入Redis并设置逻辑过期时间和真正的过期时间
                                this.setWithLogicalAndRealExpire(key, r1, time, unit);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            // 释放锁
                            unLock(lockKey);
                        }
                    });
                }
                // 返回过期的缓存数据
                return r;
            }
        }
        // 8.缓存中不存在，判断命中的是否是空值
        if (json != null) {
            // 8.1.是空值，返回错误信息
            return null;
        }
        // 9.缓存中不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 10.判断数据库中是否存在
        if (r == null) {
            // 10.1.数据库中不存在，将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 11.数据库中存在，写入Redis并设置逻辑过期时间和真正的过期时间
        this.setWithLogicalAndRealExpire(key, r, time, unit);
        return r;
    }

    //    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
//        String key = keyPrefix + id;
//        // 1.从Redis查询商铺缓存
//        String json = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isNotBlank(json)) {
//            // 3.存在，直接返回
//            return JSONUtil.toBean(json, type);
//        }
//        // 判断命中的是否是空值
//        if (json != null) {
//            // 返回错误信息
//            return null;
//        }
//        // 4.不存在，根据id查询数据库
//        R r = dbFallback.apply(id);
//        // 5.不存在，返回错误
//        if (r == null) {
//            // 将空值写入 Redis
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            // 返回错误信息
//            return null;
//        }
//        // 6.存在，写入 Redis
//        this.set(key, r, addRandomTime(time), unit);
//        return r;
//    }

//    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
//        String key = keyPrefix + id;
//        // 1.从Redis查询商铺缓存
//        String json = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isBlank(json)) {
//            // 3.不存在，直接返回
//            return null;
//        }
//        // 4.命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
//        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1.未过期，直接返回店铺信息
//            return r;
//        }
//        // 5.2.已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1.获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tyrLock(lockKey);
//
//        // 6.2.判断是否获取锁成功
//        if (isLock) {
//            // 6.3.成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    // 查询数据库
//                    R r1 = dbFallback.apply(id);
//                    // 写入Redis
//                    this.setWithLogicalExpire(key, r1, time, unit);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        //返回过期的商铺信息
//        return r;
//    }

}

