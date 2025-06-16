package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Autowired
    private Cache<Long, Shop> shopCache;

    @Override
    public Result queryById(Long id) {
        //caffeine进程缓存
        Shop shop = shopCache.get(id, key ->
                //缓存空对象解决缓存穿透，逻辑过期解决缓存击穿
                cacheClient.queryWithPassThroughAndLogicalExpire(
                        RedisConstants.CACHE_SHOP_KEY,
                        key,
                        Shop.class,
                        this::getById,
                        RedisConstants.CACHE_SHOP_TTL,
                        TimeUnit.MINUTES));
//        Shop shop = cacheClient.queryWithPassThroughAndLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithPassThrough(Long id) {
//        //从Redis中查
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
//        //判断Redis有无
//        if (StrUtil.isNotBlank(shopJson)) {
//            //有
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否命中Redis中的空值
//        if (shopJson != null) {
//            //返回错误信息
//            return null;
//        }
//        //redis中没有，查数据库
//        Shop shop = baseMapper.selectById(id);
//        //判断数据库有无
//        if (shop==null){
//            //无
//            //将空值写入Redis
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //数据库中有，加入缓存，返回店铺
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+shop.getId(), JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    public Shop queryWithMutex(Long id) {
//        //从Redis中查
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
//        //判断Redis有无
//        if (StrUtil.isNotBlank(shopJson)) {
//            //有
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否命中Redis中的空值
//        if (shopJson != null) {
//            //返回错误信息
//            return null;
//        }
//        //实现缓存重建
//
//        //获取互斥锁
//        //判断是否获取成功
//        //失败，休眠重试
//
//        //成功，根据id查询数据库
//        //redis中没有，查数据库
//        Shop shop = baseMapper.selectById(id);
//        //判断数据库有无
//        if (shop==null){
//            //无
//            //将空值写入Redis
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //数据库中有，加入缓存，返回店铺
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+shop.getId(), JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }


//    private boolean tyrLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查
        if (x == null || y == null) {
            //普通分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int form = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().radius(
                RedisConstants.SHOP_GEO_KEY + typeId,
                new Circle(new Point(x, y), new Distance(5000, Metrics.METERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
        );
        //解析出id
        if (search == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = search.getContent();
        if (list.size() <= form) {
            return Result.ok(Collections.emptyList());
        }
        //截取from~end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(form).forEach(reuslt -> {
            //获取店铺id
            String shopIdStr = reuslt.getContent().getName();
            ids.add(Long.parseLong(shopIdStr));
            //获取距离
            Distance distance = reuslt.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
