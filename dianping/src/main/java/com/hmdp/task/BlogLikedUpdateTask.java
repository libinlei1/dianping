package com.hmdp.task;

import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

//@Component
@Slf4j
public class BlogLikedUpdateTask {

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private RedissonClient redissonClient;


//    @Scheduled(cron = "0/30 * * * * ?") // 每 30 秒同步一次
    public void syncLikesToDatabase() {
        log.info("定时任务触发，尝试获取分布式锁");
        RLock lock = redissonClient.getLock("sync_likes_task_lock");
        try {
            // 尝试获取锁
            boolean isLocked = lock.tryLock();
            if (!isLocked) {
                log.info("获取锁失败，跳过任务");
                return;
            }
            log.info("获取锁成功，开始执行任务");
            rabbitTemplate.convertAndSend("liked.direct", "likedUpdate", "sync_likes");
            log.info("同步消息发送成功");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


}
