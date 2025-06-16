package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> COMMENTSLIKED_UPDATE_SCRIPT;
    static {
        COMMENTSLIKED_UPDATE_SCRIPT = new DefaultRedisScript<>();
        COMMENTSLIKED_UPDATE_SCRIPT.setLocation(new ClassPathResource("commentLikedUpdate.lua"));
        COMMENTSLIKED_UPDATE_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result getByBlogId(Long id) {
        LambdaQueryWrapper lambdaQueryWrapper = new LambdaQueryWrapper<BlogComments>()
                .eq(BlogComments::getBlogId, id);
        List<BlogComments> blogCommentsList = baseMapper.selectList(lambdaQueryWrapper);
        return Result.ok(blogCommentsList);
    }

    @Override
    public void like(Long id) {
        Long userId = UserHolder.getUser().getId();
        Long execute = redisTemplate.execute(
                COMMENTSLIKED_UPDATE_SCRIPT,
                Collections.singletonList(RedisConstants.COMMENT_LIKED_KEY + id),
                userId.toString()
        );
        int value = execute.intValue();
        if (value == 1) {
            //点赞成功
            rabbitTemplate.convertAndSend("commentsLiked.direct","likedAdd",id);
        }else {
            //取消点赞
            rabbitTemplate.convertAndSend("commentsLiked.direct","likedDec",id);
        }
    }

    @Override
    public void likedAdd(Long id) {
        update().setSql("liked=liked+1").eq("id",id).update();
    }

    @Override
    public void likedDec(Long id) {
        update().setSql("liked=liked-1").eq("id",id).update();
    }


}
