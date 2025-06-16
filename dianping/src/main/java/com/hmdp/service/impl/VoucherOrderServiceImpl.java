package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * <p>
 * 服务实现类
 * </p>
 *

 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private KafkaTemplate<String, VoucherOrder> kafkaTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    private BlockingQueue<VoucherOrder> orderTasks = new LinkedBlockingQueue<VoucherOrder>(1024*1024);

//    private static final ExecutorService SECKIL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


//    @PostConstruct
//    private void init() {
//        SECKIL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }

//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //获取队列中订单信息
//                    VoucherOrder voucherOrder=orderTasks.take();
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                    log.info("异步更新数据库库存成功");
//                } catch (Exception e) {
//                    log.error("订单处理异常",e);
//                }
//            }
//        }
//    }

//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        //获取用户
//        Long userId = voucherOrder.getUserId();
//        //创建锁对象
//        RLock lock = redissonClient.getLock("lock::order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if (!isLock) {
//            //失败
//            log.error("不允许重复抢购");
//            return;
//        }
//        try {
//            proxy.createVoucherOrder(voucherOrder);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

//    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
//        查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        //是否开始抢购
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("活动还未开始");
        }
        //是否抢购结束
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("来晚了，活动已结束");
        }
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //判断执行结果
        int i = execute.intValue();
        if (i!=0){
            return Result.fail(i==1?"库存不足！":"不能重复抢购！");
        }
        //抢购成功
        VoucherOrder voucherOrder=new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
////        保存阻塞队列
//        orderTasks.add(voucherOrder);
//        //获取代理对象
//        proxy=(IVoucherOrderService) AopContext.currentProxy();
        //
        //rabbitMQ
        try {
            rabbitTemplate.convertAndSend("seckill.direct","seckilled",voucherOrder);
            log.info("向交换机发送了秒杀成功的消息");
        } catch (Exception e) {
            log.info("秒杀信息发送失败，voucherId：{}",voucherId,"voucherOrderId:{}",orderId);
        }
        //kafka
//        try {
//            kafkaTemplate.send("seckill.success.topic", voucherOrder);
//            log.info("向Kafka主题发送了秒杀成功的消息");
//        } catch (Exception e) {
//            log.error("秒杀信息发送失败，voucherId：{}，voucherOrderId:{}", voucherId, orderId, e);
//        }
        return Result.ok(orderId);
    }


    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        Long userId = voucherOrder.getUserId();
        Integer count = query().eq("voucher_id", voucherOrder.getVoucherId())
                .eq("user_id", userId)
                .count();
        if (count > 0) {
            log.error("用户已经购买过了");
            return;
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
//                .eq("stock", voucher.getStock())//CAS乐观锁(过于严格，会造成很多抢购失败)
                .gt("stock", 0)//改进后的乐观锁，只要库存大于0就可以
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        save(voucherOrder);
    }

    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher == null) {
//            return Result.fail("优惠券不存在");
//        }
//        //是否开始抢购
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(voucher.getBeginTime())) {
//            return Result.fail("活动还未开始");
//        }
//        //是否抢购结束
//        if (now.isAfter(voucher.getEndTime())) {
//            return Result.fail("来晚了，活动已结束");
//        }
//        //判断库存是否充足
//        if (voucher.getStock() <= 0) {
//            return Result.fail("很遗憾，已经被抢完了");
//        }
//        //创建订单
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
////        boolean isLock = lock.tryLock(200l);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            //同一用户恶意多次请求
//            return Result.fail("不允许重复抢购");
//        }
//        try {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//        //下面这种锁不是分布式锁，当有两个Tomcat服务时，两个服务都能拿到锁
//        //创建订单
////        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
////            //获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//    }

}
