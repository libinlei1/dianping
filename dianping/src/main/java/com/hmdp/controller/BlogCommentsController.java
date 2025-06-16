package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.injector.methods.Update;
import com.hmdp.dto.BlogCommentsDto;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/blog-comments")
@CrossOrigin
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @PostMapping
    public Result addComment(@RequestBody BlogCommentsDto blogCommentsDto) {
        BlogComments blogComments = BeanUtil.copyProperties(blogCommentsDto, BlogComments.class);

        UserDTO user = UserHolder.getUser();
        blogComments.setUserId(user.getId());
        blogComments.setNickName(user.getNickName());
        blogComments.setIcon(user.getIcon());
        blogComments.setLiked(0);
        blogComments.setStatus(0);

        LocalDateTime now = LocalDateTime.now();
        blogComments.setCreateTime(now);
        blogComments.setUpdateTime(now);
        blogCommentsService.save(blogComments);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result getComment(@PathVariable Long id) {
        return blogCommentsService.getByBlogId(id);
    }

    @PutMapping("/like/{id}")
    public Result like(@PathVariable Long id) {
        blogCommentsService.like(id);
        return Result.ok();
    }
}
