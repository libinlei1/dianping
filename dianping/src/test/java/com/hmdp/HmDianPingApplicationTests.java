package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogDoc;
import com.hmdp.entity.Shop;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private IBlogService blogService;

//    @Test
//    void testSaveShop(){
//        Shop shop = shopServiceImpl.getById(1l);
//        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L,shop,10l, TimeUnit.SECONDS);
//    }

//    @Test
//    void loadShopData(){
//        //查询店铺信息
//        List<Shop> list = shopService.list();
//        //按照typeId分组
//        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//        //导入redis
//        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
//            Long entryKey = entry.getKey();
//            String key="shop:geo:"+entryKey;
//            List<Shop> value = entry.getValue();
//            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
//            for (Shop shop : value) {
//                locations.add(new RedisGeoCommands.GeoLocation<>(
//                        shop.getId().toString(),
//                        new Point(shop.getX(), shop.getY())
//                ));
//            }
//            stringRedisTemplate.opsForGeo().add(key,locations);
//        }
//    }

    @Test
    public void inputEs() throws IOException {
        // 分页查询商品数据
        int pageNo = 1;
        int size = 1000;
        while (true) {
            Page<Blog> page = blogService.lambdaQuery().page(new Page<Blog>(pageNo, size));
            // 非空校验
            List<Blog> blogs = page.getRecords();
            if (CollectionUtil.isEmpty(blogs)) {
                return;
            }
            log.info("加载第{}页数据，共{}条", pageNo, blogs.size());
            // 1.创建Request
            BulkRequest request = new BulkRequest("blogs");
            // 2.准备参数，添加多个新增的Request
            for (Blog blog : blogs) {
                // 2.1.转换为文档类型ItemDTO
                BlogDoc blogDoc = BeanUtil.copyProperties(blog, BlogDoc.class);
                // 2.2.创建新增文档的Request对象
                request.add(new IndexRequest()
                        .id(String.valueOf(blogDoc.getId()))
                        .source(JSONUtil.toJsonStr(blogDoc), XContentType.JSON));
            }
            // 3.发送请求
            restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
            // 翻页
            pageNo++;
        }
    }

}
