package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        //判断关注还是取关
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOWS_KEY+userId,followUserId.toString());
            }
        } else {
            //取关
            LambdaQueryWrapper lambdaQueryWrapper = new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            boolean isSuccess = remove(lambdaQueryWrapper);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOWS_KEY+userId,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper lambdaQueryWrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId);
        Follow one = getOne(lambdaQueryWrapper);
        if (one != null) {
            return Result.ok(true);
        }
        else {
            return Result.ok(false);
        }
    }

    @Override
    public Result common(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1=RedisConstants.FOLLOWS_KEY+userId;
        String key2=RedisConstants.FOLLOWS_KEY+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect==null||intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> commonUserIds=new ArrayList<>();
        intersect.forEach(commonId -> {
            long parsed = Long.parseLong(commonId);
            commonUserIds.add(parsed);
        });

        List<UserDTO> userDTOList = userService.listByIds(commonUserIds).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
