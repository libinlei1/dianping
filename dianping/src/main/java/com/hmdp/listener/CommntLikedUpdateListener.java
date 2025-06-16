package com.hmdp.listener;

import com.hmdp.service.IBlogCommentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CommntLikedUpdateListener {

    @Autowired
    private IBlogCommentsService blogCommentsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "commentLiked.add.queue",durable = "true"),
            exchange = @Exchange(name = "commentsLiked.direct"),
            key = "likedAdd"
    ))
    public void commentslikedAdd(Long id) {
        blogCommentsService.likedAdd(id);
        log.info("队列接收消息并增加了评论：{}的赞",id);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "commentLiked.dec.queue",durable = "true"),
            exchange = @Exchange(name = "commentsLiked.direct"),
            key = "likedDec"
    ))
    public void commentslikedDec(Long id) {
        blogCommentsService.likedDec(id);
        log.info("队列接收消息并减少了评论：{}的赞",id);
    }

}
