package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public Result sendCode(String phone) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //格式不正确
            return Result.fail("手机号码格式不正确");
        }
        //号码格式正确，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码(模拟)
        log.info("发送验证码成功：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //格式不正确
            return Result.fail("手机号码格式不正确");
        }
        //校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (!cacheCode.equals(loginForm.getCode()) || cacheCode == null) {
            return Result.fail("验证码错误");
        }
        //验证码正确,查询用户
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone);
        User user = getOne(lambdaQueryWrapper);
        if (user == null) {
            //新用户，保存信息
            User newUser = new User();
            newUser.setPhone(phone);
            newUser.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            baseMapper.insert(newUser);
            user = newUser;
        }
        //保存用户信息到redis
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString();
        //将user对象转变为hashmap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)//忽略空属性，不存入map
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//将属性转换为字符串
        //存储到Redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        //设置有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        //今天是当月第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        //今天是当月第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取到今天为止的签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        if (result==null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null||num==0) {
            return Result.ok(0);
        }
        //循环遍历
        int count=0;
        while (true){
            //与运算
            if ((num&1)==0){
                break;
            }else {
                count++;
            }
            //数字右移
            num>>>=1;
        }
        return Result.ok(count);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public void updateMe(UserDTO userDTO) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<User>()
                .eq("id", userDTO.getId())
                .set("nick_name", userDTO.getNickName())
                .set("icon", userDTO.getIcon());
        userMapper.update(null,updateWrapper);
    }

}
