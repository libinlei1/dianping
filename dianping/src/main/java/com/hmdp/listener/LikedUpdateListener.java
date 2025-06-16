package com.hmdp.listener;

import com.hmdp.entity.Blog;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IBlogService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikedUpdateListener {

    private final IBlogService blogService;

//    @RabbitListener(bindings = @QueueBinding(
//            value = @Queue(name = "liked.update.queue", durable = "true"),
//            exchange = @Exchange(name = "liked.direct"),
//            key = "likedUpdate"
//    ))
//    public void blogLikedUpdate(String message) {
//        if ("sync_likes".equals(message)) {
//            log.info("接收到同步消息，开始同步点赞数据");
//            Set<String> keys = stringRedisTemplate.keys("blog:liked:*");
//            if (keys != null) {
//                List<Blog> likesList = new ArrayList<>();
//                for (String key : keys) {
//                    Long blogId = Long.parseLong(key.split(":")[2]);
//                    Blog blog = blogService.getById(blogId);
//                    Long likes = stringRedisTemplate.opsForZSet().size(key); // 获取 Set 中的元素个数
//                    if (likes != null) {
//                        blog.setLiked(Math.toIntExact(likes));
//                        likesList.add(blog);
//                    }
//                }
//                blogService.updateBatch(likesList); // 批量更新数据库
//            }
//            log.info("点赞数量同步成功");
//        }
//    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "liked.add.queue",durable = "true"),
            exchange = @Exchange(name = "liked.direct"),
            key = "likedAdd"
    ))
    public void blogLikedAdd(Long id) {
        blogService.addBlogLike(id);
        log.info("队列接收消息并增加了一个赞");
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "liked.dec.queue",durable = "true"),
            exchange = @Exchange(name = "liked.direct"),
            key = "likedDec"
    ))
    public void blogLikedDec(Long id) {
        blogService.decBlogLike(id);
        log.info("队列接收消息并减少了一个赞");
    }

}
