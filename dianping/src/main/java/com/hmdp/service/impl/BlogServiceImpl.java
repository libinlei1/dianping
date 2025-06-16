package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogDoc;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private IFollowService followService;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    private static final DefaultRedisScript<Long> LIKED_UPDATE_SCRIPT;
    static {
        LIKED_UPDATE_SCRIPT=new DefaultRedisScript<>();
        LIKED_UPDATE_SCRIPT.setLocation(new ClassPathResource("likedUpdate.lua"));
        LIKED_UPDATE_SCRIPT.setResultType(Long.class);
    }




    @Override
    public Result queryBlogById(Long id) {
        Long userId1 = UserHolder.getUser().getId();
        //增加浏览量
        stringRedisTemplate.opsForHyperLogLog().add(RedisConstants.BLOG_LOOK_KEY+id, String.valueOf(userId1));
        //查询笔记
        Blog blog = baseMapper.selectById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        //查询发表笔记的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        //查询是否被当前用户点赞
        if (UserHolder.getUser() != null) {
            //有用户登录才查看是否已点赞
            Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId1.toString());
            blog.setIsLike(score != null);
        }
        Long size = stringRedisTemplate.opsForZSet().size(RedisConstants.BLOG_LIKED_KEY + id);
        blog.setLiked(Math.toIntExact(size));
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //判断用户是否已经点赞
        // 这里可以使用lua脚本保证原子性
        Long execute = stringRedisTemplate.execute(
                LIKED_UPDATE_SCRIPT,
                Collections.singletonList(RedisConstants.BLOG_LIKED_KEY + id),
                userId.toString(),
                String.valueOf(System.currentTimeMillis())
        );
        int value = execute.intValue();
        if (value==1){
            rabbitTemplate.convertAndSend("liked.direct", "likedAdd",id);
        }else {
            rabbitTemplate.convertAndSend("liked.direct", "likedDec",id);
        }
//        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
//        if (score == null) {
//            //未点赞，可以点赞
//            //点赞后将用户添加到Redis的set集
//            stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
//            rabbitTemplate.convertAndSend("liked.direct", "likedAdd",id);
//        } else {
//            //已点赞，数据库取消点赞
//            //将set集合中的用户移除
//            stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
//            rabbitTemplate.convertAndSend("liked.direct", "likedDec",id);
//        }
        return Result.ok();
    }

    @Override
    public void addBlogLike(Long id) {
        update().setSql("liked=liked+1").eq("id",id).update();
    }

    @Override
    public void decBlogLike(Long id) {
        update().setSql("liked=liked-1").eq("id",id).update();
    }


//    @Override
//    public void updateBatch(List<Blog> likesList) {
//        updateBatchById(likesList);
//    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIdList = new ArrayList<>();
        top5.forEach(userId -> {
            long parsed = Long.parseLong(userId);
            userIdList.add(parsed);
        });
        //
        String idStr = StrUtil.join(",", userIdList);

        List<UserDTO> userDTOList = userService.query().in("id", userIdList).last("order by field(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) throws IOException {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        blog.setName(user.getNickName());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //保存到es
        IndexRequest indexRequest = new IndexRequest("blogs").id(blog.getId().toString());
        BlogDoc blogDoc = BeanUtil.copyProperties(blog, BlogDoc.class);
        String jsonPrettyStr = JSONUtil.toJsonPrettyStr(blogDoc);
        indexRequest.source(jsonPrettyStr, XContentType.JSON);
        restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        //查询作者所有粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记
        followList.forEach(follow -> {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId, blog.getId().toString(), System.currentTimeMillis());
        });
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                RedisConstants.FEED_KEY + userId, 0, max, offset, 2
        );
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析数据：blogId，mintime,offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        Long minTime = 0L;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取分数（时间戳）
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        //根据id查blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            //查询发表笔记的用户
            Long id = blog.getUserId();
            User user = userService.getById(id);
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());
            Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), UserHolder.getUser().getId().toString());
            blog.setIsLike(score != null);
        }
        //封装返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    @Override
    public Result queryBlogLooked(Long id) {
        Long size = stringRedisTemplate.opsForHyperLogLog().size(RedisConstants.BLOG_LOOK_KEY + id);
        log.info("获取blogId：{}的浏览量：{}",id,size);
        return Result.ok(size);
    }

}
