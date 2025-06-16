package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillSuccessListener {

    private final IVoucherOrderService voucherOrderService;

    private final RedissonClient redissonClient;

    private static final ExecutorService SECKIL_ORDER_EXECUTOR = Executors.newFixedThreadPool(2);



    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "seckill.success.queue",durable = "true"),
            exchange = @Exchange(name = "seckill.direct"),
            key = "seckilled"
    ))
    public void listenerSeckillSuccess(VoucherOrder voucherOrder) {
        SECKIL_ORDER_EXECUTOR.submit(()->{
            //获取用户
            Long userId = voucherOrder.getUserId();
            //创建锁对象
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            boolean isLock = lock.tryLock();
            if (!isLock) {
                log.error("不允许重复抢购");
                return;
            }
          try {
              voucherOrderService.createVoucherOrder(voucherOrder);
              log.info("异步创建订单成功，订单id:{}",voucherOrder.getId());
          }catch (Exception e){
              log.error("订单处理异常:{}",e.getMessage());
          }finally {
              lock.unlock();
          }
        });
    }

//    @KafkaListener(topics = "seckill.success.topic", groupId = "seckill-group")
//    public void listenerSeckillSuccess2(VoucherOrder voucherOrder) {
//        SECKIL_ORDER_EXECUTOR.submit(() -> {
//            // 获取用户
//            Long userId = voucherOrder.getUserId();
//            // 创建锁对象
//            RLock lock = redissonClient.getLock("lock:order:" + userId);
//            boolean isLock = lock.tryLock();
//            if (!isLock) {
//                log.error("不允许重复抢购");
//                return;
//            }
//            try {
//                voucherOrderService.createVoucherOrder(voucherOrder);
//                log.info("异步创建订单成功，订单id:{}", voucherOrder.getId());
//            } catch (Exception e) {
//                log.error("订单处理异常:{}", e.getMessage());
//            } finally {
//                lock.unlock();
//            }
//        });
//    }
}
