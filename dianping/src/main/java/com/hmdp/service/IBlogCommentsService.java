package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    Result getByBlogId(Long id);

    void like(Long id);

    void likedAdd(Long id);

    void likedDec(Long id);
}
