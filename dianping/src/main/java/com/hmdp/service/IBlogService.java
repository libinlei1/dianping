package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    void addBlogLike(Long id);

    void decBlogLike(Long id);

//    void updateBatch(List<Blog> likesList);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog) throws IOException;

    Result queryBlogOfFollow(Long max, Integer offset);


    Result queryBlogLooked(Long id);
}
