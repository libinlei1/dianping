package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlogDoc {

    private Long id;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 用户图标
     */
    @TableField(exist = false)
    private String icon;
    /**
     * 用户姓名
     */
    @TableField(exist = false)
    private String name;
    /**
     * 标题
     */
    private String title;
    /**
     * 探店的照片，最多9张，多张以","隔开
     */
    private String images;
    /**
     * 点赞数量
     */
    private Integer liked;

}
