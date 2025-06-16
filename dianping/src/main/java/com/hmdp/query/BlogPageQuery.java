package com.hmdp.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlogPageQuery {

    private String title;

    private String name;

    private Integer pageNo;

    private Integer pageSize;

}
