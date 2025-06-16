package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.asm.Type;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static cn.hutool.poi.excel.sax.AttributeName.s;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryAndSort() {
        //从redis中查找
        String shoptype = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY);
        //如果不为空，直接返回缓存中的信息
        if (StrUtil.isNotBlank(shoptype)) {
            List<ShopType> shopTypeList = JSONUtil.toList(shoptype, ShopType.class);//将String类型转化为List类型
            return Result.ok(shopTypeList);
        }
        //如果为空，就从数据库中查找
        List<ShopType> ShopTypes = query().orderByAsc("sort").list();
        //如果数据库中为空，直接返回
        if (ShopTypes.isEmpty()) {
            return Result.fail("未查找到商铺信息");
        }
        //将数据缓存到Redis当中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(ShopTypes));
        return Result.ok(ShopTypes);
    }
}
