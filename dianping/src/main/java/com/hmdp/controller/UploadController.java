package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
@CrossOrigin
public class UploadController {

    @Autowired
    private FileStorageService fileStorageService;

//    @PostMapping("blog")
//    public Result uploadImage(@RequestParam("file") MultipartFile image) {
//        try {
//            // 获取原始文件名称
//            String originalFilename = image.getOriginalFilename();
//            // 生成新文件名
//            String fileName = createNewFileName(originalFilename);
//            // 保存文件
//            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
//            // 返回结果
//            log.debug("文件上传成功，{}", fileName);
//            return Result.ok(fileName);
//        } catch (IOException e) {
//            throw new RuntimeException("文件上传失败", e);
//        }
//    }

    /**
     * 上传文件，成功返回文件 url
     */
    @PostMapping("/blog")
    public Result upload2(MultipartFile file) {
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath("upload/") //保存到相对路径下，为了方便管理，不需要可以不写
//                .setSaveFilename("image.jpg") //设置保存的文件名，不需要可以不写，会随机生成
//.setObjectId("0") //关联对象id，为了方便管理，不需要可以不写
//.setObjectType("0") //关联对象类型，为了方便管理，不需要可以不写
                .putAttr("role","admin") //保存一些属性，可以在切面、保存上传记录、自定义存储平台等地方获取使用，不需要可以不写
                .upload(); //将文件上传到对应地方
        return Result.ok(fileInfo == null ? "上传失败！" : fileInfo.getUrl());
    }

    @PostMapping("/comment")
    public Result upload3(MultipartFile file) {
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath("comment/") //保存到相对路径下，为了方便管理，不需要可以不写
//                .setSaveFilename("image.jpg") //设置保存的文件名，不需要可以不写，会随机生成
//.setObjectId("0") //关联对象id，为了方便管理，不需要可以不写
//.setObjectType("0") //关联对象类型，为了方便管理，不需要可以不写
                .putAttr("role","admin") //保存一些属性，可以在切面、保存上传记录、自定义存储平台等地方获取使用，不需要可以不写
                .upload(); //将文件上传到对应地方
        return Result.ok(fileInfo == null ? "上传失败！" : fileInfo.getUrl());
    }

    @PostMapping("/userIcon")
    public Result upload4(MultipartFile file) {
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath("userIcon/") //保存到相对路径下，为了方便管理，不需要可以不写
//                .setSaveFilename("image.jpg") //设置保存的文件名，不需要可以不写，会随机生成
//.setObjectId("0") //关联对象id，为了方便管理，不需要可以不写
//.setObjectType("0") //关联对象类型，为了方便管理，不需要可以不写
                .putAttr("role","admin") //保存一些属性，可以在切面、保存上传记录、自定义存储平台等地方获取使用，不需要可以不写
                .upload(); //将文件上传到对应地方
        return Result.ok(fileInfo == null ? "上传失败！" : fileInfo.getUrl());
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
