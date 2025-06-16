package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/userInfo")
public class UserInfoController {

    @Resource
    private IUserInfoService userInfoService;

    @GetMapping
    public Result meInfo(){
        Long id = UserHolder.getUser().getId();
        UserInfo userInfo = userInfoService.getById(id);
        return Result.ok(userInfo);
    }

    @PostMapping
    public Result update(@RequestBody UserInfo userInfo){
        userInfoService.updateById(userInfo);
        return Result.ok();
    }


}
